
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.data.projection

import com.vgomez.app.data.projection
import com.vgomez.app.data.projection.Model._
import com.vgomez.app.domain.DomainModel.{Role, Timetable}
import com.vgomez.app.domain.Transformer.FromDomainToRawData._
import com.vgomez.app.domain.Transformer.FromRawDataToDomain._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

object Model {
  // Models

  final case class RestaurantModel(index: Option[Long], id: String, username: String, name: String,
    state: String, city: String, postalCode: String, latitude: Double, longitude: Double,
    categories: List[String], timetable: Timetable)

  final case class ReviewModel(index: Option[Long], id: String, username: String, restaurantId: String,
    stars: Int, text: String, date: String)

  final case class UserModel(index: Option[Long], username: String, password: String, role: Role,
    latitude: Double, longitude: Double, favoriteCategories: List[String])
}

object Response {
  case class GetRestaurantModelsResponse(restaurantModels: Seq[RestaurantModel])

  case class GetReviewModelsResponse(reviewModels: Seq[ReviewModel])

  case class GetUserModelsResponse(userModels: Seq[UserModel])

  case class GetReviewModelsStarsResponse(reviewModelsStars: Seq[Int])
}

object Table {
  // Table
  val api: projection.CustomPostgresProfile.CustomPGAPI.type = CustomPostgresProfile.api

  import api._

  val schemaName: String = "reviews"

  class RestaurantTable(tag: Tag) extends Table[RestaurantModel](tag, Some(schemaName),
    "Restaurant") {
    implicit val timetableMapper: JdbcType[Timetable] with BaseTypedType[Timetable] =
      MappedColumnType.base[Timetable, String](
        e => transformTimetableToString(e),
        s => transformTimetableStringToTimetable(s)
      )

    def index: Rep[Long] = column[Long]("index", O.PrimaryKey, O.AutoInc)

    def id: Rep[String] = column[String]("id")

    def username: Rep[String] = column[String]("username")

    def name: Rep[String] = column[String]("name")

    def state: Rep[String] = column[String]("state")

    def city: Rep[String] = column[String]("city")

    def postalCode: Rep[String] = column[String]("postalCode")

    def latitude: Rep[Double] = column[Double]("latitude")

    def longitude: Rep[Double] = column[Double]("longitude")

    def categories: Rep[List[String]] = column[List[String]]("categories")

    def timetable: Rep[Timetable] = column[Timetable]("timetable")

    override def * = (index.?, id, username, name, state, city, postalCode,
      latitude, longitude, categories, timetable) <> (RestaurantModel.tupled, RestaurantModel.unapply)
  }

  lazy val restaurantTable = TableQuery[RestaurantTable]

  class ReviewTable(tag: Tag) extends Table[ReviewModel](tag, Some(schemaName), "Review") {
    def index: Rep[Long] = column[Long]("index", O.PrimaryKey, O.AutoInc)

    def id: Rep[String] = column[String]("id")

    def username: Rep[String] = column[String]("username")

    def restaurantId: Rep[String] = column[String]("restaurant_id")

    def stars: Rep[Int] = column[Int]("stars")

    def text: Rep[String] = column[String]("text")

    def date: Rep[String] = column[String]("date")

    override def * = (index.?, id, username, restaurantId, stars, text,
      date) <> (ReviewModel.tupled, ReviewModel.unapply)
  }

  lazy val reviewTable = TableQuery[ReviewTable]

  class UserTable(tag: Tag) extends Table[UserModel](tag, Some(schemaName), "User") {
    implicit val roleMapper: JdbcType[Role] with BaseTypedType[Role] = MappedColumnType.base[Role, String](
      e => transformRoleToStringRole(e),
      s => transformStringRoleToRole(s)
    )

    def index: Rep[Long] = column[Long]("index", O.PrimaryKey, O.AutoInc)

    def username: Rep[String] = column[String]("username")

    def password: Rep[String] = column[String]("password")

    def role: Rep[Role] = column[Role]("role")

    def latitude: Rep[Double] = column[Double]("latitude")

    def longitude: Rep[Double] = column[Double]("longitude")

    def favoriteCategories: Rep[List[String]] = column[List[String]]("favorite_categories")

    override def * = (index.?, username, password, role, latitude, longitude,
      favoriteCategories) <> (UserModel.tupled, UserModel.unapply)
  }

  lazy val userTable = TableQuery[UserTable]

  val tables = Seq(restaurantTable, reviewTable, userTable)
  val ddl = tables.map(_.schema).reduce(_ ++ _)
}

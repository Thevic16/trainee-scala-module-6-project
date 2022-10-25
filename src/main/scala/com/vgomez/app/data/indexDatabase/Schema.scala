package com.vgomez.app.data.indexDatabase

import com.vgomez.app.domain.DomainModel.{Role, Schedule}
import com.vgomez.app.domain.Transformer.FromDomainToRawData._
import com.vgomez.app.domain.Transformer.FromRawDataToDomain._

object Model {
  // Models

  final case class RestaurantModel(index: Option[Long], id: String, username: String, name: String, state: String,
                                   city: String, postalCode: String, latitude: Double, longitude: Double,
                                   categories: List[String], schedule: Schedule)

  final case class ReviewModel(index: Option[Long], id: String, username: String, restaurantId: String, stars: Int,
                               text: String, date: String)


  final case class UserModel(index: Option[Long], username: String, password: String, role: Role, latitude: Double,
                             longitude: Double, favoriteCategories: List[String])

}

object Response {
  import Model._
  case class GetRestaurantModelsResponse(restaurantModels: Seq[RestaurantModel])
  case class GetReviewModelsResponse(reviewModels: Seq[ReviewModel])
  case class GetUserModelsResponse(userModels: Seq[UserModel])

  case class GetReviewModelsStarsResponse(reviewModelsStars: Seq[Int])

  case class GetSequenceReviewModelsStarsResponse(seqReviewModelsStars: Seq[Seq[Int]])

}

object Table {
  // Table
  import Model._
  val api = CustomPostgresProfile.api
  import api._

  val schemaName: String = "reviews"

  class RestaurantTable(tag: Tag) extends Table[RestaurantModel](tag, Some(schemaName), "Restaurant") {
    implicit val scheduleMapper = MappedColumnType.base[Schedule, String](
      e => transformScheduleToScheduleString(e),
      s => transformScheduleStringToSchedule(s)
    )

    def index = column[Long]("index", O.PrimaryKey, O.AutoInc)

    def id = column[String]("id")

    def username = column[String]("username")

    def name = column[String]("name")

    def state = column[String]("state")

    def city = column[String]("city")

    def postalCode = column[String]("postalCode")

    def latitude = column[Double]("latitude")

    def longitude = column[Double]("longitude")

    def categories = column[List[String]]("categories")

    def schedule = column[Schedule]("schedule")

    override def * = (index.?, id, username, name, state, city, postalCode,
                         latitude, longitude, categories, schedule) <> (RestaurantModel.tupled, RestaurantModel.unapply)
  }
  lazy val restaurantTable = TableQuery[RestaurantTable]

  class ReviewTable(tag: Tag) extends Table[ReviewModel](tag, Some(schemaName), "Review") {
    def index = column[Long]("index", O.PrimaryKey, O.AutoInc)

    def id = column[String]("id")

    def username = column[String]("username")

    def restaurantId = column[String]("restaurant_id")

    def stars = column[Int]("stars")

    def text = column[String]("text")

    def date = column[String]("date")

    override def * = (index.?, id, username, restaurantId, stars, text,
                                                date) <> (ReviewModel.tupled, ReviewModel.unapply)
  }
  lazy val reviewTable = TableQuery[ReviewTable]

  class UserTable(tag: Tag) extends Table[UserModel](tag, Some(schemaName), "User") {
    implicit val roleMapper = MappedColumnType.base[Role, String](
      e => transformRoleToStringRole(e),
      s => transformStringRoleToRole(s)
    )

    def index = column[Long]("index", O.PrimaryKey, O.AutoInc)

    def username = column[String]("username")

    def password = column[String]("password")

    def role = column[Role]("role")

    def latitude = column[Double]("latitude")

    def longitude = column[Double]("longitude")

    def favoriteCategories = column[List[String]]("favorite_categories")

    override def * = (index.?, username, password, role, latitude, longitude,
                                              favoriteCategories) <> (UserModel.tupled, UserModel.unapply)
  }
  lazy val userTable = TableQuery[UserTable]

  val tables = Seq(restaurantTable, reviewTable, userTable)
  val ddl = tables.map(_.schema).reduce(_ ++ _)
}

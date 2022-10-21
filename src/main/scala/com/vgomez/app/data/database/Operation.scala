package com.vgomez.app.data.database

import com.vgomez.app.data.database.Model._
import com.vgomez.app.data.database.Table.{RestaurantTable, restaurantTable}
import com.vgomez.app.domain.DomainModelOperation.rangeInKmToDegrees

import scala.concurrent.Future
import slick.jdbc.GetResult

object Operation {
  import Table.api._
  val db = Connection.db

  /*
  Todo take into account pagination in getAllQueries and others
  */
  def getAllRestaurantModel(pageNumber: Long, numberOfElementPerPage: Long): Future[Seq[RestaurantModel]] = {
    val query = Table.restaurantTable.filter(restaurant => restaurant.index >= pageNumber*numberOfElementPerPage &&
                                  restaurant.index <= pageNumber*numberOfElementPerPage + numberOfElementPerPage).result
    db.run(query)
  }

  def getAllReviewModel(pageNumber: Long, numberOfElementPerPage: Long): Future[Seq[ReviewModel]] = {
    val query = Table.reviewTable.filter(review => review.index >= pageNumber * numberOfElementPerPage &&
                                    review.index <= pageNumber * numberOfElementPerPage + numberOfElementPerPage).result
    db.run(query)
  }

  def getAllUserModel(pageNumber: Long, numberOfElementPerPage: Long): Future[Seq[UserModel]] = {
    val query = Table.userTable.filter(user => user.index >= pageNumber * numberOfElementPerPage &&
                                      user.index <= pageNumber * numberOfElementPerPage + numberOfElementPerPage).result
    db.run(query)
  }

  def insertRestaurantModel(restaurantModel: RestaurantModel): Future[Int] = {
    val insertQuery = Table.restaurantTable forceInsert restaurantModel
    db.run(insertQuery)
  }

  def insertReviewModel(reviewModel: ReviewModel): Future[Int] = {
    val insertQuery = Table.reviewTable forceInsert reviewModel
    db.run(insertQuery)
  }

  def insertUserModel(userModel: UserModel): Future[Int] = {
    val insertQuery = Table.userTable forceInsert userModel
    db.run(insertQuery)
  }

  def updateRestaurantModel(id: String, restaurantModel: RestaurantModel): Future[Int] = {
    val updateQuery = Table.restaurantTable.filter(_.id === id).update(restaurantModel)
    db.run(updateQuery)
  }

  def updateReviewModel(id: String, reviewModel: ReviewModel): Future[Int] = {
    val updateQuery = Table.reviewTable.filter(_.id === id).update(reviewModel)
    db.run(updateQuery)
  }

  def updateUserModel(username: String, userModel: UserModel): Future[Int] = {
    val updateQuery = Table.userTable.filter(_.username === username).update(userModel)
    db.run(updateQuery)
  }

  def deleteRestaurantModel(id: String): Future[Int] = {
    val deleteQuery = Table.restaurantTable.filter(_.id === id).delete
    db.run(deleteQuery)
  }

  def deleteReviewModel(id: String): Future[Int] = {
    val deleteQuery = Table.reviewTable.filter(_.id === id).delete
    db.run(deleteQuery)
  }

  def deleteUserModel(username: String): Future[Int] = {
    val deleteQuery = Table.userTable.filter(_.username === username).delete
    db.run(deleteQuery)
  }

  def getReviewsModelByRestaurantId(restaurantId: String): Future[Seq[ReviewModel]] = {
    val query = Table.reviewTable.filter(_.restaurantId === restaurantId).result
    db.run(query)
  }

  def getRestaurantsModelByCategories(categories: List[String], pageNumber: Long,
                                      numberOfElementPerPage: Long): Future[Seq[RestaurantModel]] = {
    val query = Table.restaurantTable.filter(_.categories @& categories.bind).drop(pageNumber*numberOfElementPerPage)
                                                                                    .take(numberOfElementPerPage).result
    db.run(query)
  }

  def getPosiblesRestaurantsModelByLocation(queryLatitude: Double, queryLongitude: Double,
                                            rangeInKm: Double, pageNumber: Long,
                                            numberOfElementPerPage: Long): Future[Seq[RestaurantModel]] = {
    val query = Table.restaurantTable.filter(conditionPosibleRestaurantsModelByLocation(queryLatitude, queryLongitude,
                                                                                rangeInKm, _)).
                                           drop(pageNumber * numberOfElementPerPage).take(numberOfElementPerPage).result
    db.run(query)
  }

  def conditionPosibleRestaurantsModelByLocation(queryLatitude: Double, queryLongitude: Double, rangeInKm: Double,
                                                 restaurant: RestaurantTable): Rep[Boolean] = {
    val rangeInDegrees = rangeInKmToDegrees(rangeInKm)

    (restaurant.latitude >= queryLatitude - rangeInDegrees || restaurant.latitude <= queryLatitude + rangeInDegrees) &&
      (restaurant.longitude >= queryLongitude - rangeInDegrees || restaurant.longitude <= queryLongitude + rangeInDegrees)
  }

}

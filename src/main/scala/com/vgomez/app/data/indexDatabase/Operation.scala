package com.vgomez.app.data.indexDatabase

import com.vgomez.app.data.indexDatabase.Model._
import com.vgomez.app.data.indexDatabase.Response._
import com.vgomez.app.data.indexDatabase.Table.{RestaurantTable, restaurantTable}
import com.vgomez.app.domain.DomainModelOperation.rangeInKmToDegrees
import ExecContext._
import akka.Done

import scala.concurrent.Future

object Operation {
  import Table.api._
  val db = Connection.db

  /*
  Todo take into account pagination in getAllQueries and others
  */
  def getAllRestaurantModel(pageNumber: Long, numberOfElementPerPage: Long): Future[GetRestaurantModelsResponse] = {
    val query = Table.restaurantTable.filter(restaurant => restaurant.index >= pageNumber*numberOfElementPerPage &&
                                  restaurant.index <= pageNumber*numberOfElementPerPage + numberOfElementPerPage).result
    db.run(query).map(GetRestaurantModelsResponse)
  }

  def getAllReviewModel(pageNumber: Long, numberOfElementPerPage: Long): Future[GetReviewModelsResponse] = {
    val query = Table.reviewTable.filter(review => review.index >= pageNumber * numberOfElementPerPage &&
                                    review.index <= pageNumber * numberOfElementPerPage + numberOfElementPerPage).result
    db.run(query).map(GetReviewModelsResponse)
  }

  def getAllUserModel(pageNumber: Long, numberOfElementPerPage: Long): Future[GetUserModelsResponse] = {
    val query = Table.userTable.filter(user => user.index >= pageNumber * numberOfElementPerPage &&
                                      user.index <= pageNumber * numberOfElementPerPage + numberOfElementPerPage).result
    db.run(query).map(GetUserModelsResponse)
  }

  def insertRestaurantModel(restaurantModel: RestaurantModel): Future[Done] = {
    val insertQuery = Table.restaurantTable forceInsert restaurantModel
    db.run(insertQuery).map(_ => Done)
  }

  def insertReviewModel(reviewModel: ReviewModel): Future[Done] = {
    val insertQuery = Table.reviewTable forceInsert reviewModel
    db.run(insertQuery).map(_ => Done)
  }

  def insertUserModel(userModel: UserModel): Future[Done] = {
    val insertQuery = Table.userTable forceInsert userModel
    db.run(insertQuery).map(_ => Done)
  }

  def updateRestaurantModel(id: String, restaurantModel: RestaurantModel): Future[Done] = {
    val updateQuery = Table.restaurantTable.filter(_.id === id).update(restaurantModel)
    db.run(updateQuery).map(_ => Done)
  }

  def updateReviewModel(id: String, reviewModel: ReviewModel): Future[Done] = {
    val updateQuery = Table.reviewTable.filter(_.id === id).update(reviewModel)
    db.run(updateQuery).map(_ => Done)
  }

  def updateUserModel(username: String, userModel: UserModel): Future[Done] = {
    val updateQuery = Table.userTable.filter(_.username === username).update(userModel)
    db.run(updateQuery).map(_ => Done)
  }

  def deleteRestaurantModel(id: String): Future[Done] = {
    val deleteQuery = Table.restaurantTable.filter(_.id === id).delete
    db.run(deleteQuery).map(_ => Done)
  }

  def deleteReviewModel(id: String): Future[Done] = {
    val deleteQuery = Table.reviewTable.filter(_.id === id).delete
    db.run(deleteQuery).map(_ => Done)
  }

  def deleteUserModel(username: String): Future[Done] = {
    val deleteQuery = Table.userTable.filter(_.username === username).delete
    db.run(deleteQuery).map(_ => Done)
  }

  def getReviewsStarsByRestaurantId(restaurantId: String): Future[GetReviewModelsStarsResponse] = {
    val query = Table.reviewTable.filter(_.restaurantId === restaurantId).map(_.stars).result
    db.run(query).map(GetReviewModelsStarsResponse)
  }

  def getReviewsStarsByListRestaurantId(seqRestaurantId: Seq[String]): Future[GetSequenceReviewModelsStarsResponse] = {
    val seqQueries = seqRestaurantId.map(restaurantId =>
      Table.reviewTable.filter(_.restaurantId === restaurantId).map(_.stars).result)

    val combineQueries = DBIO.sequence(seqQueries)
    db.run(combineQueries).map(GetSequenceReviewModelsStarsResponse)
  }

  def getRestaurantsModelByCategories(categories: List[String], pageNumber: Long,
                                      numberOfElementPerPage: Long): Future[GetRestaurantModelsResponse] = {
    val query = Table.restaurantTable.filter(_.categories @& categories.bind).drop(pageNumber*numberOfElementPerPage)
                                                                                    .take(numberOfElementPerPage).result
    db.run(query).map(GetRestaurantModelsResponse)
  }

  def getPosiblesRestaurantsModelByLocation(queryLatitude: Double, queryLongitude: Double,
                                            rangeInKm: Double, pageNumber: Long,
                                            numberOfElementPerPage: Long): Future[GetRestaurantModelsResponse] = {
    val query = Table.restaurantTable.filter(conditionPosibleRestaurantsModelByLocation(queryLatitude, queryLongitude,
                                                                                rangeInKm, _)).
                                           drop(pageNumber * numberOfElementPerPage).take(numberOfElementPerPage).result
    db.run(query).map(GetRestaurantModelsResponse)
  }

  def conditionPosibleRestaurantsModelByLocation(queryLatitude: Double, queryLongitude: Double, rangeInKm: Double,
                                                 restaurant: RestaurantTable): Rep[Boolean] = {
    val rangeInDegrees = rangeInKmToDegrees(rangeInKm)

    (restaurant.latitude >= queryLatitude - rangeInDegrees || restaurant.latitude <= queryLatitude + rangeInDegrees) &&
      (restaurant.longitude >= queryLongitude - rangeInDegrees || restaurant.longitude <= queryLongitude + rangeInDegrees)
  }

}

package com.vgomez.app.data.indexDatabase

import com.vgomez.app.data.indexDatabase.Model._
import com.vgomez.app.data.indexDatabase.Response._
import com.vgomez.app.data.indexDatabase.Table.RestaurantTable
import com.vgomez.app.domain.DomainModelOperation.rangeInKmToDegrees
import ExecContext._
import akka.Done

import scala.concurrent.Future

/*
Todo
  Description: The reading approach of the application is very complicated, it should be better to use a second index
               database to read the information from there.
  State: Done
  Reported by: Sebastian Oliveri.
*/
object Operation {
  import Table.api._
  val db = Connection.db


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

  def registerRestaurantModel(restaurantModel: RestaurantModel): Future[Either[Int, Done]] = {
    val insertQuery = Table.restaurantTable forceInsert restaurantModel
    db.run(insertQuery).map(response => if(response == 1) Right(Done) else Left(response))
  }

  def registerReviewModel(reviewModel: ReviewModel): Future[Either[Int, Done]] = {
    val insertQuery = Table.reviewTable forceInsert reviewModel
    db.run(insertQuery).map(response => if(response == 1) Right(Done) else Left(response))
  }

  def registerUserModel(userModel: UserModel): Future[Either[Int, Done]] = {
    val insertQuery = Table.userTable forceInsert userModel
    db.run(insertQuery).map(response => if(response == 1) Right(Done) else Left(response))
  }

  def updateRestaurantModel(id: String, restaurantModel: RestaurantModel): Future[Either[Int, Done]] = {
    val updateQuery = Table.restaurantTable.filter(_.id === id).update(restaurantModel)
    db.run(updateQuery).map(response => if(response == 1) Right(Done) else Left(response))
  }

  def updateReviewModel(id: String, reviewModel: ReviewModel): Future[Either[Int, Done]] = {
    val updateQuery = Table.reviewTable.filter(_.id === id).update(reviewModel)
    db.run(updateQuery).map(response => if(response == 1) Right(Done) else Left(response))
  }

  def updateUserModel(username: String, userModel: UserModel): Future[Either[Int, Done]] = {
    val updateQuery = Table.userTable.filter(_.username === username).update(userModel)
    db.run(updateQuery).map(response => if(response == 1) Right(Done) else Left(response))
  }

  def unregisterRestaurantModel(id: String): Future[Either[Int, Done]] = {
    val deleteQuery = Table.restaurantTable.filter(_.id === id).delete
    db.run(deleteQuery).map(response => if(response == 1) Right(Done) else Left(response))
  }

  def unregisterReviewModel(id: String): Future[Either[Int, Done]] = {
    val deleteQuery = Table.reviewTable.filter(_.id === id).delete
    db.run(deleteQuery).map(response => if(response == 1) Right(Done) else Left(response))
  }

  def unregisterUserModel(username: String): Future[Either[Int, Done]] = {
    val deleteQuery = Table.userTable.filter(_.username === username).delete
    db.run(deleteQuery).map(response => if(response == 1) Right(Done) else Left(response))
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
      (restaurant.longitude >= queryLongitude - rangeInDegrees || restaurant.longitude <= queryLongitude +
        rangeInDegrees)
  }

}

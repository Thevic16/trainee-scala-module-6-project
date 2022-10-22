package com.vgomez.app.actors.writers

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.pipe
import com.vgomez.app.actors.Restaurant.RestaurantInfo
import com.vgomez.app.actors.Review.ReviewInfo
import com.vgomez.app.actors.User.UserInfo
import com.vgomez.app.data.database.Model._
import com.vgomez.app.data.database.Operation._

object WriterToIndexDatabase {

  object Command {
    case class CreateRestaurant(id: String, index: Long, restaurantInfo: RestaurantInfo)
    case class UpdateRestaurant(id: String, index: Long, restaurantInfo: RestaurantInfo)
    case class DeleteRestaurant(id: String)

    case class CreateReview(id: String, index: Long, reviewInfo: ReviewInfo)
    case class UpdateReview(id: String, index: Long, reviewInfo: ReviewInfo)
    case class DeleteReview(id: String)

    case class CreateUser(index: Long, userInfo: UserInfo)
    case class UpdateUser(index: Long, userInfo: UserInfo)
    case class DeleteUser(username: String)
  }

  def props(system: ActorSystem): Props = Props(new WriterToIndexDatabase(system))

}

class WriterToIndexDatabase(system: ActorSystem) extends Actor with ActorLogging{
  import WriterToIndexDatabase.Command._
  import system.dispatcher

  override def receive: Receive = {
    case CreateRestaurant(id, index, restaurantInfo) =>
      insertRestaurantModel(getRestaurantModelByRestaurantInfo(id, index, restaurantInfo)).mapTo[Int].pipeTo(self)

    case UpdateRestaurant(id, index, restaurantInfo) =>
      updateRestaurantModel(id, getRestaurantModelByRestaurantInfo(id, index, restaurantInfo)).mapTo[Int].pipeTo(self)

    case DeleteRestaurant(id) =>
      deleteRestaurantModel(id).mapTo[Int].pipeTo(self)

    case CreateReview(id, index, reviewInfo) =>
      insertReviewModel(getReviewModelByReviewInfo(id, index, reviewInfo)).mapTo[Int].pipeTo(self)

    case UpdateReview(id, index, reviewInfo) =>
      updateReviewModel(id, getReviewModelByReviewInfo(id, index, reviewInfo)).mapTo[Int].pipeTo(self)

    case DeleteReview(id) =>
      deleteReviewModel(id).mapTo[Int].pipeTo(self)

    case CreateUser(index, userInfo) =>
      insertUserModel(getUserModelByUserInfo(index, userInfo)).mapTo[Int].pipeTo(self)

    case UpdateUser(index, userInfo) =>
      updateUserModel(userInfo.username, getUserModelByUserInfo(index, userInfo)).mapTo[Int].pipeTo(self)

    case DeleteUser(username) =>
      deleteUserModel(username).mapTo[Int].pipeTo(self)

    case indexDatabaseResponse: Int => log.info(s"Index database has response with $indexDatabaseResponse")

  }


  def getRestaurantModelByRestaurantInfo(id: String, index: Long, restaurantInfo: RestaurantInfo): RestaurantModel = {
    RestaurantModel(Some(index), id, restaurantInfo.username, restaurantInfo.name,
      restaurantInfo.state, restaurantInfo.city, restaurantInfo.postalCode, restaurantInfo.location.latitude,
      restaurantInfo.location.longitude, restaurantInfo.categories.toList, restaurantInfo.schedule)
  }

  def getReviewModelByReviewInfo(id: String, index: Long, reviewInfo: ReviewInfo): ReviewModel = {
    ReviewModel(Some(index), id, reviewInfo.username, reviewInfo.restaurantId, reviewInfo.stars, reviewInfo.text,
      reviewInfo.date)
  }

  def getUserModelByUserInfo(index: Long, userInfo: UserInfo): UserModel = {
    UserModel(Some(index), userInfo.username, userInfo.password, userInfo.role, userInfo.location.latitude,
      userInfo.location.longitude, userInfo.favoriteCategories.toList)
  }

}

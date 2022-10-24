package com.vgomez.app.actors.writers

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.pipe
import com.vgomez.app.actors.Restaurant.RestaurantInfo
import com.vgomez.app.actors.Review.ReviewInfo
import com.vgomez.app.actors.User.UserInfo
import com.vgomez.app.data.indexDatabase.Model._
import com.vgomez.app.data.indexDatabase.Operation._

object WriterToIndexDatabase {

  object Command {
    case class RegisterRestaurant(id: String, index: Long, restaurantInfo: RestaurantInfo)
    case class UpdateRestaurant(id: String, index: Long, restaurantInfo: RestaurantInfo)
    case class UnregisterRestaurant(id: String)

    case class RegisterReview(id: String, index: Long, reviewInfo: ReviewInfo)
    case class UpdateReview(id: String, index: Long, reviewInfo: ReviewInfo)
    case class UnregisterReview(id: String)

    case class RegisterUser(index: Long, userInfo: UserInfo)
    case class UpdateUser(index: Long, userInfo: UserInfo)
    case class UnregisterUser(username: String)
  }

  def props(system: ActorSystem): Props = Props(new WriterToIndexDatabase(system))

}

class WriterToIndexDatabase(system: ActorSystem) extends Actor with ActorLogging{
  import WriterToIndexDatabase.Command._
  import system.dispatcher

  override def receive: Receive = {
    case RegisterRestaurant(id, index, restaurantInfo) =>
      registerRestaurantModel(getRestaurantModelByRestaurantInfo(id, index, restaurantInfo)).mapTo[Int].pipeTo(self)

    case UpdateRestaurant(id, index, restaurantInfo) =>
      updateRestaurantModel(id, getRestaurantModelByRestaurantInfo(id, index, restaurantInfo)).mapTo[Int].pipeTo(self)

    case UnregisterRestaurant(id) =>
      unregisterRestaurantModel(id).mapTo[Int].pipeTo(self)

    case RegisterReview(id, index, reviewInfo) =>
      registerReviewModel(getReviewModelByReviewInfo(id, index, reviewInfo)).mapTo[Int].pipeTo(self)

    case UpdateReview(id, index, reviewInfo) =>
      updateReviewModel(id, getReviewModelByReviewInfo(id, index, reviewInfo)).mapTo[Int].pipeTo(self)

    case UnregisterReview(id) =>
      unregisterReviewModel(id).mapTo[Int].pipeTo(self)

    case RegisterUser(index, userInfo) =>
      registerUserModel(getUserModelByUserInfo(index, userInfo)).mapTo[Int].pipeTo(self)

    case UpdateUser(index, userInfo) =>
      updateUserModel(userInfo.username, getUserModelByUserInfo(index, userInfo)).mapTo[Int].pipeTo(self)

    case UnregisterUser(username) =>
      unregisterUserModel(username).mapTo[Int].pipeTo(self)

    case Done => log.info(s"Index database has response with Done.")
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

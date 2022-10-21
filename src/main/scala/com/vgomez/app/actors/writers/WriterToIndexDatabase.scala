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
    case class CreateRestaurant(id: String, restaurantInfo: RestaurantInfo)
    case class UpdateRestaurant(id: String, restaurantInfo: RestaurantInfo)
    case class DeleteRestaurant(id: String)

    case class CreateReview(id: String, reviewInfo: ReviewInfo)
    case class UpdateReview(id: String, reviewInfo: ReviewInfo)
    case class DeleteReview(id: String)

    case class CreateUser(userInfo: UserInfo)
    case class UpdateUser(userInfo: UserInfo)
    case class DeleteUser(username: String)
  }

  def props(system: ActorSystem): Props = Props(new WriterToIndexDatabase(system))

}

class WriterToIndexDatabase(system: ActorSystem) extends Actor with ActorLogging{
  import WriterToIndexDatabase.Command._
  import system.dispatcher

  override def receive: Receive = {
    case CreateRestaurant(id , restaurantInfo) =>
      insertRestaurantModel(getRestaurantModelByRestaurantInfo(id, restaurantInfo)).mapTo[Int].pipeTo(self)

    case UpdateRestaurant(id , restaurantInfo) =>
      updateRestaurantModel(id, getRestaurantModelByRestaurantInfo(id, restaurantInfo)).mapTo[Int].pipeTo(self)

    case DeleteRestaurant(id) =>
      deleteRestaurantModel(id).mapTo[Int].pipeTo(self)

    case CreateReview(id, reviewInfo) =>
      insertReviewModel(getReviewModelByReviewInfo(id, reviewInfo)).mapTo[Int].pipeTo(self)

    case UpdateReview(id, reviewInfo) =>
      updateReviewModel(id, getReviewModelByReviewInfo(id, reviewInfo)).mapTo[Int].pipeTo(self)

    case DeleteReview(id) =>
      deleteRestaurantModel(id).mapTo[Int].pipeTo(self)

    case CreateUser(userInfo) =>
      insertUserModel(getUserModelByUserInfo(userInfo)).mapTo[Int].pipeTo(self)

    case UpdateUser(userInfo) =>
      updateUserModel(userInfo.username, getUserModelByUserInfo(userInfo)).mapTo[Int].pipeTo(self)

    case DeleteUser(username) =>
      deleteUserModel(username).mapTo[Int].pipeTo(self)

    case indexDatabaseResponse: Int => log.info(s"Index database has response with $indexDatabaseResponse")

  }


  def getRestaurantModelByRestaurantInfo(id: String, restaurantInfo: RestaurantInfo): RestaurantModel = {
    RestaurantModel(None, id, restaurantInfo.username, restaurantInfo.name,
      restaurantInfo.state, restaurantInfo.city, restaurantInfo.postalCode, restaurantInfo.location.latitude,
      restaurantInfo.location.longitude, restaurantInfo.categories.toList, restaurantInfo.schedule)
  }

  def getReviewModelByReviewInfo(id: String, reviewInfo: ReviewInfo): ReviewModel = {
    ReviewModel(None, id, reviewInfo.username, reviewInfo.restaurantId, reviewInfo.stars, reviewInfo.text,
      reviewInfo.date)
  }

  def getUserModelByUserInfo(userInfo: UserInfo): UserModel = {
    UserModel(None, userInfo.username, userInfo.password, userInfo.role, userInfo.location.latitude,
      userInfo.location.longitude, userInfo.favoriteCategories.toList)
  }

}

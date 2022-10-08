package com.vgomez.app.http.messages
import spray.json.DefaultJsonProtocol

import com.vgomez.app.actors.Restaurant._
import com.vgomez.app.actors.Restaurant.Command._
import com.vgomez.app.actors.Review._
import com.vgomez.app.actors.Review.Command._
import com.vgomez.app.actors.User._
import com.vgomez.app.actors.User.Command._
import com.vgomez.app.domain.{DomainModel, SimpleScheduler}
import com.vgomez.app.domain.Transformer._

object HttpRequest{
  case class RestaurantCreationRequest(userId: String, name: String, state: String, city: String, postalCode: String,
                                       latitude: Double, longitude: Double, categories: Set[String],
                                       schedule: SimpleScheduler) {
    def toCommand: CreateRestaurant = CreateRestaurant(None, RestaurantInfo(userId, name, state, city, postalCode,
      DomainModel.Location(latitude, longitude), categories: Set[String], transformSimpleSchedulerToSchedule(schedule)))
  }

  trait RestaurantCreationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val restaurantCreationRequestJson = jsonFormat9(RestaurantCreationRequest)
  }

  case class RestaurantUpdateRequest(userId: String, name: String, state: String, city: String, postalCode: String,
                                     latitude: Double, longitude: Double, categories: Set[String],
                                     schedule: SimpleScheduler) {
    def toCommand(id: String): UpdateRestaurant = UpdateRestaurant(id, RestaurantInfo(userId, name, state, city, postalCode,
      DomainModel.Location(latitude, longitude), categories: Set[String], transformSimpleSchedulerToSchedule(schedule)))
  }

  trait RestaurantUpdateRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val restaurantUpdateRequestJson = jsonFormat9(RestaurantUpdateRequest)
  }

  case class ReviewCreationRequest(userId: String, restaurantId: String, stars: Int, text: String, date: String) {
    def toCommand: CreateReview = CreateReview(None, ReviewInfo(userId, restaurantId, stars, text, date))
  }

  trait ReviewCreationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val reviewCreationRequestJson = jsonFormat5(ReviewCreationRequest)
  }

  case class ReviewUpdateRequest(userId: String, restaurantId: String, stars: Int, text: String, date: String) {
    def toCommand(id: String): UpdateReview = UpdateReview(id, ReviewInfo(userId, restaurantId, stars, text, date))
  }

  trait ReviewUpdateRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val reviewUpdateRequestJson = jsonFormat5(ReviewUpdateRequest)
  }

  case class UserCreationRequest(username: String, password: String, role: String, latitude: Double, longitude: Double,
                                 favoriteCategories: Set[String]) {
    def toCommand: CreateUser = CreateUser(UserInfo(username, password, transformStringRoleToRole(role),
      DomainModel.Location(latitude, longitude), favoriteCategories))
  }

  trait UserCreationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val userCreationRequestJson = jsonFormat6(UserCreationRequest)
  }

  case class UserUpdateRequest(username: String, password: String, role: String, latitude: Double, longitude: Double,
                               favoriteCategories: Set[String]) {
    def toCommand: UpdateUser = UpdateUser(UserInfo(username, password, transformStringRoleToRole(role),
      DomainModel.Location(latitude, longitude), favoriteCategories))
  }

  trait UserUpdateRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val userUpdateRequestJson = jsonFormat6(UserUpdateRequest)
  }

  case class GetRecommendationFilterByFavoriteCategoriesRequest(favoriteCategories: Set[String])

  trait GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val getRecommendationFilterByFavoriteCategoriesRequestJsonProtocol =
                                                         jsonFormat1(GetRecommendationFilterByFavoriteCategoriesRequest)
  }

  case class GetRecommendationFilterByUserFavoriteCategoriesRequest(username: String)

  trait GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol =
      jsonFormat1(GetRecommendationFilterByUserFavoriteCategoriesRequest)
  }

}
object HttpResponse{
  // Resquest clases
  case class RestaurantResponse(id: String, userId: String, name: String, state: String, city: String, postalCode: String,
                                latitude: Double, longitude: Double, categories: Set[String],
                                schedule: SimpleScheduler, starts: Int)
  trait RestaurantResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val restaurantResponseJson = jsonFormat11(RestaurantResponse)
  }

  case class ReviewResponse(id: String, userId: String, restaurantId: String, stars: Int, text: String, date: String)

  trait ReviewResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val reviewResponseJson = jsonFormat6(ReviewResponse)
  }

  case class UserResponse(username: String, password: String, role: String, latitude: Double, longitude: Double,
                          favoriteCategories: Set[String])

  trait UserResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val userResponseJson = jsonFormat6(UserResponse)
  }

  case class FailureResponse(reason: String)

  trait FailureResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val failureResponseJson = jsonFormat1(FailureResponse)
  }

}

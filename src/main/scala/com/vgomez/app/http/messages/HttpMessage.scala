package com.vgomez.app.http.messages
import spray.json.DefaultJsonProtocol
import com.vgomez.app.actors.Restaurant._
import com.vgomez.app.actors.Restaurant.Command._
import com.vgomez.app.actors.Review._
import com.vgomez.app.actors.Review.Command._
import com.vgomez.app.actors.User._
import com.vgomez.app.actors.User.Command._
import com.vgomez.app.domain.{DomainModel, SimpleScheduler}
import com.vgomez.app.domain.Transformer.FromRawDataToDomain._
import com.vgomez.app.domain.Transformer._

object HttpRequest{
  case class RestaurantCreationRequest(username: String, name: String, state: String, city: String, postalCode: String,
                                       latitude: Double, longitude: Double, categories: Set[String],
                                       schedule: SimpleScheduler) {
    def toCommand: RegisterRestaurant = RegisterRestaurant(None, RestaurantInfo(username, name, state, city, postalCode,
      DomainModel.Location(latitude, longitude), categories: Set[String], transformSimpleSchedulerToSchedule(schedule)))
  }

  trait RestaurantCreationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val restaurantCreationRequestJson = jsonFormat9(RestaurantCreationRequest)
  }

  case class RestaurantUpdateRequest(username: String, name: String, state: String, city: String, postalCode: String,
                                     latitude: Double, longitude: Double, categories: Set[String],
                                     schedule: SimpleScheduler) {
    def toCommand(id: String): UpdateRestaurant = UpdateRestaurant(id, RestaurantInfo(username, name, state, city,
      postalCode, DomainModel.Location(latitude, longitude), categories: Set[String],
      transformSimpleSchedulerToSchedule(schedule)))
  }

  trait RestaurantUpdateRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val restaurantUpdateRequestJson = jsonFormat9(RestaurantUpdateRequest)
  }

  case class ReviewCreationRequest(username: String, restaurantId: String, stars: Int, text: String, date: String) {
    def toCommand: RegisterReview = RegisterReview(None, ReviewInfo(username, restaurantId, stars, text, date))
  }

  trait ReviewCreationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val reviewCreationRequestJson = jsonFormat5(ReviewCreationRequest)
  }

  case class ReviewUpdateRequest(username: String, restaurantId: String, stars: Int, text: String, date: String) {
    def toCommand(id: String): UpdateReview = UpdateReview(id, ReviewInfo(username, restaurantId, stars, text, date))
  }

  trait ReviewUpdateRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val reviewUpdateRequestJson = jsonFormat5(ReviewUpdateRequest)
  }

  case class UserCreationRequest(username: String, password: String, role: String, latitude: Double, longitude: Double,
                                 favoriteCategories: Set[String]) {
    def toCommand: RegisterUser = RegisterUser(UserInfo(username, password, transformStringRoleToRole(role),
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
    implicit val getRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol =
      jsonFormat1(GetRecommendationFilterByUserFavoriteCategoriesRequest)
  }

  case class GetRecommendationCloseToLocationRequest(latitude: Double, longitude: Double, rangeInKm: Double)

  trait GetRecommendationCloseToLocationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val getRecommendationCloseToLocationRequestJsonProtocol =
      jsonFormat3(GetRecommendationCloseToLocationRequest)
  }

  case class GetRecommendationCloseToMeRequest(username: String, rangeInKm: Double)

  trait GetRecommendationCloseToMeRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val getRecommendationCloseToMeRequestJsonProtocol =
      jsonFormat2(GetRecommendationCloseToMeRequest)
  }

}
object HttpResponse{
  // Resquest clases
  case class RestaurantResponse(id: String, username: String, name: String, state: String, city: String,
                                postalCode: String, latitude: Double, longitude: Double, categories: Set[String],
                                timetable: Either[String, SimpleScheduler])
  trait RestaurantResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val restaurantResponseJson = jsonFormat10(RestaurantResponse)
  }

  case class StarsResponse(stars: Int)

  trait StarsResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val starsResponseJson = jsonFormat1(StarsResponse)
  }

  case class ReviewResponse(id: String, username: String, restaurantId: String, stars: Int, text: String, date: String)

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

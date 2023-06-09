
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.http.messages

import com.vgomez.app.actors.Restaurant.Command._
import com.vgomez.app.actors.Restaurant._
import com.vgomez.app.actors.Review.Command._
import com.vgomez.app.actors.Review._
import com.vgomez.app.actors.User.Command._
import com.vgomez.app.actors.User._
import com.vgomez.app.domain.Transformer.FromRawDataToDomain._
import com.vgomez.app.domain.Transformer._
import com.vgomez.app.domain.{DomainModel, SimpleScheduler}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object HttpRequest {
  case class RestaurantCreationRequest(username: String, name: String, state: String, city: String,
    postalCode: String, latitude: Double, longitude: Double, categories: Set[String],
    schedule: SimpleScheduler) {
    def toCommand: RegisterRestaurant = RegisterRestaurant(None, RestaurantInfo(username, name, state, city,
      postalCode, DomainModel.Location(latitude, longitude), categories: Set[String],
      transformSimpleSchedulerToSchedule(schedule)))
  }

  trait RestaurantCreationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val restaurantCreationRequestJson: RootJsonFormat[RestaurantCreationRequest] =
      jsonFormat9(RestaurantCreationRequest)
  }

  case class RestaurantUpdateRequest(username: String, name: String, state: String, city: String,
    postalCode: String, latitude: Double, longitude: Double, categories: Set[String],
    schedule: SimpleScheduler) {
    def toCommand(id: String): UpdateRestaurant = UpdateRestaurant(id, RestaurantInfo(username, name, state,
      city, postalCode, DomainModel.Location(latitude, longitude), categories: Set[String],
      transformSimpleSchedulerToSchedule(schedule)))
  }

  trait RestaurantUpdateRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val restaurantUpdateRequestJson: RootJsonFormat[RestaurantUpdateRequest] =
      jsonFormat9(RestaurantUpdateRequest)
  }

  case class ReviewCreationRequest(username: String, restaurantId: String, stars: Int, text: String,
    date: String) {
    def toCommand: RegisterReview = RegisterReview(None, ReviewInfo(username, restaurantId, stars, text,
      date))
  }

  trait ReviewCreationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val reviewCreationRequestJson: RootJsonFormat[ReviewCreationRequest] =
      jsonFormat5(ReviewCreationRequest)
  }

  case class ReviewUpdateRequest(username: String, restaurantId: String, stars: Int, text: String,
    date: String) {
    def toCommand(id: String): UpdateReview = UpdateReview(id, ReviewInfo(username, restaurantId, stars, text,
      date))
  }

  trait ReviewUpdateRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val reviewUpdateRequestJson: RootJsonFormat[ReviewUpdateRequest] =
      jsonFormat5(ReviewUpdateRequest)
  }

  case class UserCreationRequest(username: String, password: String, role: String, latitude: Double,
    longitude: Double, favoriteCategories: Set[String]) {
    def toCommand: RegisterUser = RegisterUser(UserInfo(username, password, transformStringRoleToRole(role),
      DomainModel.Location(latitude, longitude), favoriteCategories))
  }

  trait UserCreationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val userCreationRequestJson: RootJsonFormat[UserCreationRequest] =
      jsonFormat6(UserCreationRequest)
  }

  case class UserUpdateRequest(username: String, password: String, role: String, latitude: Double,
    longitude: Double, favoriteCategories: Set[String]) {
    def toCommand: UpdateUser = UpdateUser(UserInfo(username, password, transformStringRoleToRole(role),
      DomainModel.Location(latitude, longitude), favoriteCategories))
  }

  trait UserUpdateRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val userUpdateRequestJson: RootJsonFormat[UserUpdateRequest] = jsonFormat6(UserUpdateRequest)
  }

  case class GetRecommendationFilterByFavoriteCategoriesRequest(favoriteCategories: Set[String])

  trait GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val getRecommendationFilterByFavoriteCategoriesRequestJsonProtocol:
      RootJsonFormat[GetRecommendationFilterByFavoriteCategoriesRequest] =
      jsonFormat1(GetRecommendationFilterByFavoriteCategoriesRequest)
  }

  case class GetRecommendationFilterByUserFavoriteCategoriesRequest(username: String)

  trait GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val getRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol:
      RootJsonFormat[GetRecommendationFilterByUserFavoriteCategoriesRequest] =
      jsonFormat1(GetRecommendationFilterByUserFavoriteCategoriesRequest)
  }

  case class GetRecommendationCloseToLocationRequest(latitude: Double, longitude: Double, rangeInKm: Double)

  trait GetRecommendationCloseToLocationRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val getRecommendationCloseToLocationRequestJsonProtocol:
      RootJsonFormat[GetRecommendationCloseToLocationRequest] = jsonFormat3(
      GetRecommendationCloseToLocationRequest)
  }

  case class GetRecommendationCloseToMeRequest(username: String, rangeInKm: Double)

  trait GetRecommendationCloseToMeRequestJsonProtocol extends DefaultJsonProtocol {
    implicit val getRecommendationCloseToMeRequestJsonProtocol:
      RootJsonFormat[GetRecommendationCloseToMeRequest] = jsonFormat2(GetRecommendationCloseToMeRequest)
  }

}

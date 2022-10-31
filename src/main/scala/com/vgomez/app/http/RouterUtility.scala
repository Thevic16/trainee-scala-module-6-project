package com.vgomez.app.http

import com.vgomez.app.actors.Restaurant.Response._
import com.vgomez.app.actors.Restaurant.{RegisterRestaurantState, RestaurantState}
import com.vgomez.app.actors.Review.RegisterReviewState
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.User.RegisterUserState
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.domain.DomainModel.{Schedule, UnavailableTimetable}
import com.vgomez.app.domain.SimpleScheduler
import com.vgomez.app.domain.Transformer.FromDomainToRawData._
import com.vgomez.app.http.messages.HttpResponse._


object RouterUtility {

  /*
  Todo #1
    Description: Decouple restaurant.
    Action: Remove start from this method.
    Status: Done
    Reported by: Sebastian Oliveri.
  */
  def getRestaurantResponseByGetRestaurantResponse(getRestaurantResponse: GetRestaurantResponse): RestaurantResponse = {
    getRestaurantResponse match {
      case GetRestaurantResponse(Some(restaurantState)) =>
        getRestaurantResponseByRestaurantState(restaurantState)
    }
  }

  def getReviewResponseByGetReviewResponse(getReviewResponse: GetReviewResponse): ReviewResponse = {
    getReviewResponse match {
      case GetReviewResponse(Some(reviewState)) =>
        reviewState match {
          case RegisterReviewState(id, _, username, restaurantId, stars, text, date) =>
            ReviewResponse(id, username, restaurantId, stars, text, date)
        }
    }
  }

  def getUserResponseByGetUserResponse(getUserResponse: GetUserResponse): UserResponse = {
    getUserResponse match {
      case GetUserResponse(Some(userState)) =>
        userState match {
          case RegisterUserState(username, _, password, role, location, favoriteCategories) =>
            UserResponse(username, password, transformRoleToStringRole(role),
              location.latitude, location.longitude, favoriteCategories)
        }
    }
  }

  /*
  Todo #1
    Description: Decouple restaurant.
    Action: Remove start from this method.
    Status: Done
    Reported by: Sebastian Oliveri.
  */
  def getRestaurantResponseByRestaurantState(restaurantState: RestaurantState): RestaurantResponse = {

    restaurantState match {
      case RegisterRestaurantState(id, _, username, name, state, city, postalCode, location, categories, timetable) =>
        val timetableRawData: Either[String, SimpleScheduler] = timetable match {
          case schedule@Schedule(_) => Right(transformScheduleToSimpleScheduler(schedule))
          case UnavailableTimetable => Left("NULL")
        }

          RestaurantResponse(id, username, name,
          state, city, postalCode,
          location.latitude, location.longitude, categories, timetableRawData)
    }
  }
}

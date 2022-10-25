package com.vgomez.app.http

import com.vgomez.app.actors.Restaurant.Response._
import com.vgomez.app.actors.Restaurant.{RegisterRestaurantState, RestaurantState}
import com.vgomez.app.actors.Review.RegisterReviewState
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.User.RegisterUserState
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.domain.DomainModel.{Schedule, UnavailableTimetable}
import com.vgomez.app.domain.SimpleScheduler
import com.vgomez.app.domain.Transformer.FromRawDataToDomain._
import com.vgomez.app.domain.Transformer.FromDomainToRawData._
import com.vgomez.app.http.messages.HttpResponse._


object RouterUtility {

  def getRestaurantResponseByGetRestaurantResponse(getRestaurantResponse: GetRestaurantResponse): RestaurantResponse = {
    getRestaurantResponse match {
      case GetRestaurantResponse(Some(restaurantState), Some(stars)) =>
        getRestaurantResponseByRestaurantState(restaurantState, stars)
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
          case RegisterUserState(username, index, password, role, location, favoriteCategories) =>
            UserResponse(username, password, transformRoleToStringRole(role),
              location.latitude, location.longitude, favoriteCategories)
        }
    }
  }

  def getRestaurantResponseByRestaurantState(restaurantState: RestaurantState, stars: Int): RestaurantResponse = {

    restaurantState match {
      case RegisterRestaurantState(id, _, username, name, state, city, postalCode, location, categories, timetable) =>
        val timetableRawData: Either[String, SimpleScheduler] = timetable match {
          case schedule@Schedule(_) => Right(transformScheduleToSimpleScheduler(schedule))
          case UnavailableTimetable => Left("NULL")
        }

          RestaurantResponse(id, username, name,
          state, city, postalCode,
          location.latitude, location.longitude, categories, timetableRawData, stars)
    }
  }
}

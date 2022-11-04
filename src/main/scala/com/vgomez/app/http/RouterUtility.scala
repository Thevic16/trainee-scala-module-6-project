
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.http

import com.vgomez.app.actors.Restaurant.{RegisterRestaurantState, RestaurantState}
import com.vgomez.app.actors.Review.{RegisterReviewState, ReviewState}
import com.vgomez.app.actors.User.{RegisterUserState, UserState}
import com.vgomez.app.domain.DomainModel.{Schedule, UnavailableTimetable}
import com.vgomez.app.domain.SimpleScheduler
import com.vgomez.app.domain.Transformer.FromDomainToRawData._
import com.vgomez.app.http.messages.HttpResponse._


object RouterUtility {
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

  def getReviewResponseByReviewState(reviewState: ReviewState): ReviewResponse = {
    reviewState match {
      case RegisterReviewState(id, _, username, restaurantId, stars, text, date) =>
        ReviewResponse(id, username, restaurantId, stars, text, date)
    }
  }

  def getUserResponseByUserState(userState: UserState): UserResponse = {
    userState match {
      case RegisterUserState(username, _, password, role, location, favoriteCategories) =>
        UserResponse(username, password, transformRoleToStringRole(role), location.latitude, location.longitude,
          favoriteCategories)
    }
  }

}

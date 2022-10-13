package com.vgomez.app.http

import com.vgomez.app.actors.Restaurant.Response._
import com.vgomez.app.actors.Restaurant.RestaurantState
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.domain.Transformer._
import com.vgomez.app.http.messages.HttpResponse._


object RouterUtility {

  def getRestaurantResponseByGetRestaurantResponse(getRestaurantResponse: GetRestaurantResponse): RestaurantResponse = {
    getRestaurantResponse match {
      case GetRestaurantResponse(Some(restaurantState), Some(starts)) =>
        RestaurantResponse(restaurantState.id, restaurantState.username, restaurantState.name, restaurantState.state,
          restaurantState.city, restaurantState.postalCode, restaurantState.location.latitude,
          restaurantState.location.longitude, restaurantState.categories,
          transformScheduleToSimpleScheduler(restaurantState.schedule), starts)
    }
  }

  def getReviewResponseByGetReviewResponse(getReviewResponse: GetReviewResponse): ReviewResponse = {
    getReviewResponse match {
      case GetReviewResponse(Some(reviewState)) =>
        ReviewResponse(reviewState.id, reviewState.username, reviewState.restaurantId, reviewState.stars, reviewState.text,
          reviewState.date)
    }
  }

  def getUserResponseByGetUserResponse(getUserResponse: GetUserResponse): UserResponse = {
    getUserResponse match {
      case GetUserResponse(Some(userState)) =>
        UserResponse(userState.username, userState.password, transformRoleToStringRole(userState.role),
          userState.location.latitude, userState.location.longitude, userState.favoriteCategories)
    }
  }

  def getRestaurantResponseByRestaurantState(restaurantState: RestaurantState, stars: Int): RestaurantResponse = {
    RestaurantResponse(restaurantState.id, restaurantState.username, restaurantState.name,
      restaurantState.state, restaurantState.city, restaurantState.postalCode,
      restaurantState.location.latitude, restaurantState.location.longitude,
      restaurantState.categories, transformScheduleToSimpleScheduler(restaurantState.schedule), stars)
  }

}

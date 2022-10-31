package com.vgomez.app.actors.writers

import com.vgomez.app.actors.Restaurant.RegisterRestaurantState
import com.vgomez.app.actors.Review.RegisterReviewState
import com.vgomez.app.actors.User.RegisterUserState
import com.vgomez.app.data.projectionDatabase.Model._


object ProjectionHandlerUtility {
  def getRestaurantModelByRegisterRestaurantState(registerRestaurantState: RegisterRestaurantState): RestaurantModel = {
    RestaurantModel(Some(registerRestaurantState.index), registerRestaurantState.id, registerRestaurantState.username,
      registerRestaurantState.name, registerRestaurantState.state, registerRestaurantState.city,
      registerRestaurantState.postalCode, registerRestaurantState.location.latitude,
      registerRestaurantState.location.longitude, registerRestaurantState.categories.toList,
      registerRestaurantState.timetable)
  }

  def getReviewModelByRegisterReviewState(registerReviewState: RegisterReviewState): ReviewModel = {
    ReviewModel(Some(registerReviewState.index), registerReviewState.id, registerReviewState.username,
      registerReviewState.restaurantId, registerReviewState.stars, registerReviewState.text, registerReviewState.date)
  }

  def getUserModelByRegisterUserState(registerUserState: RegisterUserState): UserModel = {
    UserModel(Some(registerUserState.index), registerUserState.username, registerUserState.password,
      registerUserState.role, registerUserState.location.latitude, registerUserState.location.longitude,
      registerUserState.favoriteCategories.toList)
  }
}

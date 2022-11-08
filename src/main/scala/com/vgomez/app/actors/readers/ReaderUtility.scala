
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.actors.readers

import com.vgomez.app.actors.Restaurant.{RegisterRestaurantState, RestaurantState}
import com.vgomez.app.actors.Review.{RegisterReviewState, ReviewState}
import com.vgomez.app.actors.User.{RegisterUserState, UserState}
import com.vgomez.app.data.projectionDatabase.Model.{RestaurantModel, ReviewModel, UserModel}
import com.vgomez.app.domain.DomainModel.Location

object ReaderUtility {

  def getRestaurantStateByRestaurantModel(restaurantModel: RestaurantModel): RestaurantState = {
    RegisterRestaurantState(restaurantModel.id, restaurantModel.index.getOrElse(0L), restaurantModel.username,
      restaurantModel.name, restaurantModel.state, restaurantModel.city, restaurantModel.postalCode,
      Location(restaurantModel.latitude, restaurantModel.longitude), restaurantModel.categories.toSet,
      restaurantModel.timetable)
  }

  def getReviewStateByReviewModel(reviewModel: ReviewModel): ReviewState = {
    RegisterReviewState(reviewModel.id, reviewModel.index.getOrElse(0L), reviewModel.username,
      reviewModel.restaurantId, reviewModel.stars, reviewModel.text, reviewModel.date)
  }

  def getUserStateByUserModel(userModel: UserModel): UserState = {
    RegisterUserState(userModel.username, userModel.index.getOrElse(0L), userModel.password, userModel.role,
      Location(userModel.latitude, userModel.longitude), userModel.favoriteCategories.toSet)
  }

  def getListRestaurantStateBySeqRestaurantModels(restaurantModels: Seq[RestaurantModel]):
  List[RestaurantState] = {
    restaurantModels.map(getRestaurantStateByRestaurantModel).toList
  }

  def getListReviewStateBySeqReviewModels(reviewModels: Seq[ReviewModel]):
  List[ReviewState] = {
    reviewModels.map(getReviewStateByReviewModel).toList
  }

  def getListUserStateBySeqReviewModels(userModels: Seq[UserModel]):
  List[UserState] = {
    userModels.map(getUserStateByUserModel).toList
  }

  def getRecommendationResponseBySeqRestaurantModels(restaurantModels:
                                                     Seq[RestaurantModel]): Option[List[RestaurantState]] = {
    if (restaurantModels.nonEmpty) {
      Some(getListRestaurantStateBySeqRestaurantModels(restaurantModels))
    } else {
      None
    }
  }
}

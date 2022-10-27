package com.vgomez.app.actors.readers

import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.Restaurant.{RegisterRestaurantState, RestaurantState}
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.Review.{RegisterReviewState, ReviewState}
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.User.{RegisterUserState, UserState}
import com.vgomez.app.data.indexDatabase.Model.{RestaurantModel, ReviewModel, UserModel}
import com.vgomez.app.domain.DomainModel.Location

object ReaderUtility {

  def getRestaurantStateByRestaurantModel(restaurantModel: RestaurantModel): RestaurantState = {
    RegisterRestaurantState(restaurantModel.id, restaurantModel.index.getOrElse(0L), restaurantModel.username,
      restaurantModel.name, restaurantModel.state, restaurantModel.city, restaurantModel.postalCode,
      Location(restaurantModel.latitude, restaurantModel.longitude), restaurantModel.categories.toSet,
      restaurantModel.timetable)
  }

  def getReviewStateByReviewModel(reviewModel: ReviewModel): ReviewState = {
    RegisterReviewState(reviewModel.id, reviewModel.index.getOrElse(0L), reviewModel.username, reviewModel.restaurantId,
      reviewModel.stars, reviewModel.text, reviewModel.date)
  }

  def getUserStateByUserModel(userModel: UserModel): UserState = {
    RegisterUserState(userModel.username, userModel.index.getOrElse(0L), userModel.password, userModel.role,
      Location(userModel.latitude, userModel.longitude), userModel.favoriteCategories.toSet)
  }

  /*
  Todo #3
    Description: Decouple restaurant.
    Action: Remove stars parameter from getRestaurantResponseByRestaurantModel method.
    Status: Done
    Reported by: Sebastian Oliveri.
  */
  def getRestaurantResponseByRestaurantModel(restaurantModel: RestaurantModel): GetRestaurantResponse = {
    GetRestaurantResponse(Some(getRestaurantStateByRestaurantModel(restaurantModel)))
  }

  /*
  Todo #3
    Description: Decouple restaurant.
    Action: Remove stars parameter from getListRestaurantResponsesBySeqRestaurantModels method.
    Status: Done
    Reported by: Sebastian Oliveri.
  */
  def getListRestaurantResponsesBySeqRestaurantModels(restaurantModels: Seq[RestaurantModel]):
  List[GetRestaurantResponse] = {
    restaurantModels.map(getRestaurantResponseByRestaurantModel).toList
  }

  def getReviewResponseByReviewModel(reviewModel: ReviewModel): GetReviewResponse = {
    GetReviewResponse(Some(getReviewStateByReviewModel(reviewModel)))
  }

  def getListReviewResponsesBySeqReviewModels(reviewModels: Seq[ReviewModel]):
  List[GetReviewResponse] = {
    reviewModels.map(getReviewResponseByReviewModel).toList
  }

  def getUserResponseByUserModel(userModel: UserModel): GetUserResponse = {
    GetUserResponse(Some(getUserStateByUserModel(userModel)))
  }

  def getListUserResponsesBySeqReviewModels(userModels: Seq[UserModel]):
  List[GetUserResponse] = {
    userModels.map(getUserResponseByUserModel).toList
  }

}

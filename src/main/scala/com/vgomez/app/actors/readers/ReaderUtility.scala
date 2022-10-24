package com.vgomez.app.actors.readers

import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.Restaurant.RestaurantState
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.Review.ReviewState
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.User.UserState
import com.vgomez.app.data.indexDatabase.Model
import com.vgomez.app.data.indexDatabase.Model.{RestaurantModel, ReviewModel, UserModel}
import com.vgomez.app.domain.DomainModel.Location

object ReaderUtility {
  def getRestaurantResponseByRestaurantModel(restaurantModel: RestaurantModel,
                                             reviewsStars: Seq[Int]): GetRestaurantResponse = {
    if(reviewsStars.nonEmpty){
      GetRestaurantResponse(Some(getRestaurantStateByRestaurantModel(restaurantModel)),
        Some(reviewsStars.sum / reviewsStars.length))
    }
    else {
      GetRestaurantResponse(Some(getRestaurantStateByRestaurantModel(restaurantModel)), Some(0))
    }
  }

  def getRestaurantStateByRestaurantModel(restaurantModel: RestaurantModel): RestaurantState = {
    RestaurantState(restaurantModel.id, restaurantModel.index.getOrElse(0L),restaurantModel.username,
      restaurantModel.name, restaurantModel.state, restaurantModel.city, restaurantModel.postalCode,
      Location(restaurantModel.latitude, restaurantModel.longitude), restaurantModel.categories.toSet,
      restaurantModel.schedule, isDeleted = false)
  }

  def getListRestaurantResponsesBySeqRestaurantModels(restaurantModels: Seq[Model.RestaurantModel],
                                                      reviewsStars: Seq[Seq[Int]]): List[GetRestaurantResponse] = {

    def go(restaurantModels: Seq[Model.RestaurantModel], reviewsStars: Seq[Seq[Int]],
           getRestaurantResponses: List[GetRestaurantResponse] = List()): List[GetRestaurantResponse] = {
      if(restaurantModels.isEmpty || reviewsStars.isEmpty) getRestaurantResponses
      else go(restaurantModels.tail, reviewsStars.tail,
        getRestaurantResponses :+ getRestaurantResponseByRestaurantModel(restaurantModels.head, reviewsStars.head))
    }

    go(restaurantModels, reviewsStars)
  }

  def getReviewStateByReviewModel(reviewModel: ReviewModel): ReviewState = {
    ReviewState(reviewModel.id, reviewModel.index.getOrElse(0L), reviewModel.username, reviewModel.restaurantId,
      reviewModel.stars, reviewModel.text, reviewModel.date, isDeleted = false)
  }

  def getReviewResponseByReviewModel(reviewModel: ReviewModel): GetReviewResponse = {
    GetReviewResponse(Some(getReviewStateByReviewModel(reviewModel)))
  }

  def getUserStateByUserModel(userModel: UserModel): UserState = {
    UserState(userModel.username, userModel.index.getOrElse(0L), userModel.password, userModel.role,
      Location(userModel.latitude, userModel.longitude), userModel.favoriteCategories.toSet, isDeleted = false)
  }

  def getUserResponseByUserModel(userModel: UserModel): GetUserResponse = {
    GetUserResponse(Some(getUserStateByUserModel(userModel)))
  }

}

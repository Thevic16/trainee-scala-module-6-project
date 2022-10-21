package com.vgomez.app.actors.readers

import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.Restaurant.RestaurantState
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Response.GetResponse
import com.vgomez.app.data.database.Model
import com.vgomez.app.data.database.Model.RestaurantModel
import com.vgomez.app.domain.DomainModel.Location

object ReaderUtility {

  def getRestaurantResponseList(accResponses: List[GetResponse]): List[GetRestaurantResponse] = {
    accResponses.collect {
      case getRestaurantResponse@GetRestaurantResponse(_, _) => getRestaurantResponse
    }
  }

  def getReviewResponseList(accResponses: List[GetResponse]): List[GetReviewResponse] = {
    accResponses.collect {
      case getReviewResponse@GetReviewResponse(_) => getReviewResponse
    }
  }

  def getUserResponseList(accResponses: List[GetResponse]): List[GetUserResponse] = {
    accResponses.collect {
      case getUserResponse@GetUserResponse(_) => getUserResponse
    }
  }

  def getRestaurantResponseByRestaurantModel(restaurantModel: RestaurantModel,
                                             reviewsStars: Seq[Int]): GetRestaurantResponse = {
    GetRestaurantResponse(Some(getRestaurantStateByRestaurantModel(restaurantModel)),
      Some(reviewsStars.sum / reviewsStars.length))
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

}

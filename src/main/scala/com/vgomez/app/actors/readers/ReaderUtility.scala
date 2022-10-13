package com.vgomez.app.actors.readers

import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Response.GetResponse

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

}

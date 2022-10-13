package com.vgomez.app.actors.readers

import akka.actor.ActorContext
import com.vgomez.app.actors.Restaurant.Command.GetRestaurant
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.Review.Command.GetReview
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Response.GetResponse
import com.vgomez.app.actors.readers.ReaderGetAll.ActorType

object ReaderUtility {

//  def getRestaurantResponse(getResponse: GetResponse): GetRestaurantResponse = {
//    getResponse match {
//      case getRestaurantResponse@GetRestaurantResponse(_, _) => getRestaurantResponse
//    }
//  }
//
//  def getReviewResponse(getResponse: GetResponse): GetReviewResponse = {
//    getResponse match {
//      case getReviewResponse@GetReviewResponse(_) => getReviewResponse
//    }
//  }
//
//  def getUserResponse(getResponse: GetResponse): GetUserResponse = {
//    getResponse match {
//      case getUserResponse@GetUserResponse(_) => getUserResponse
//    }
//  }

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

package com.vgomez.app.actors.readers


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.intermediate.IntermediateReadUserAttributes.Command.GetUserFavoriteCategories
import com.vgomez.app.actors.messages.AbstractMessage.Response.GetRecommendationResponse
import com.vgomez.app.actors.readers.ReaderUtility.getListRestaurantResponsesBySeqRestaurantModels
import com.vgomez.app.data.projectionDatabase.Operation
import com.vgomez.app.data.projectionDatabase.Response.GetRestaurantModelsResponse

object ReaderFilterByCategories {
  // commands
  object Command {
    // Recommendations Categories
    case class GetRecommendationFilterByFavoriteCategories(favoriteCategories: Set[String], pageNumber: Long,
                                                           numberOfElementPerPage: Long)
    case class GetRecommendationFilterByUserFavoriteCategories(username: String, pageNumber: Long,
                                                               numberOfElementPerPage: Long)
  }

  def props(system: ActorSystem, intermediateReadUserAttributes: ActorRef): Props =
    Props(new ReaderFilterByCategories(system, intermediateReadUserAttributes))
}

/*
Todo #3
  Description: Decouple Actor eliminate halfway methods.
  Action: Add intermediateReadUserAttributes Actor.
  Status: Done
  Reported by: Sebastian Oliveri.
*/
class ReaderFilterByCategories(system: ActorSystem,
                               intermediateReadUserAttributes: ActorRef) extends Actor with ActorLogging with Stash {
  import ReaderFilterByCategories._
  import Command._
  import system.dispatcher


  def state(): Receive = {
    case GetRecommendationFilterByFavoriteCategories(favoriteCategories, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByFavoriteCategories command.")
      Operation.getRestaurantsModelByCategories(favoriteCategories.toList, pageNumber,
        numberOfElementPerPage).mapTo[GetRestaurantModelsResponse].pipeTo(self)
      unstashAll()
      context.become(getRestaurantsState(sender()))

    case GetRecommendationFilterByUserFavoriteCategories(username, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByUserFavoriteCategories command.")
      intermediateReadUserAttributes ! GetUserFavoriteCategories(username)
      unstashAll()
      context.become(intermediateGetUserFavoriteCategoriesState(sender(), pageNumber, numberOfElementPerPage))

    case _ =>
      stash()
  }
  
  def getRestaurantsState(originalSender: ActorRef): Receive = {
    /*
    Todo #1
      Description: Decouple restaurant.
      Action: Remove stars request on the database and only left restaurant models.
      Status: Done
      Reported by: Sebastian Oliveri.
    */
    case GetRestaurantModelsResponse(restaurantModels) =>
      if (restaurantModels.nonEmpty)
        originalSender ! GetRecommendationResponse(Some(
                                                     getListRestaurantResponsesBySeqRestaurantModels(restaurantModels)))
      else originalSender ! GetRecommendationResponse(None)

      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  /*
  Todo #3
    Description: Decouple Actor eliminate halfway methods.
    Action: Let the responsibility to get user favoriteCategories to other actor.
    Status: Done
    Reported by: Sebastian Oliveri.
  */
  def intermediateGetUserFavoriteCategoriesState(originalSender: ActorRef, pageNumber: Long,
                                                             numberOfElementPerPage: Long): Receive = {
    case Some(favoriteCategories: Set[String]) =>
      Operation.getRestaurantsModelByCategories(favoriteCategories.toList, pageNumber,
        numberOfElementPerPage).mapTo[GetRestaurantModelsResponse].pipeTo(self)
      unstashAll()
      context.become(getRestaurantsState(originalSender))

    case None =>
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}

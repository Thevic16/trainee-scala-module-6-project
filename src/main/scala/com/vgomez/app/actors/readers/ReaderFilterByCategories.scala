package com.vgomez.app.actors.readers


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.{RegisterUserState, UnregisterUserState}
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.messages.AbstractMessage.Response.GetRecommendationResponse
import com.vgomez.app.actors.readers.ReaderUtility.getListRestaurantResponsesBySeqRestaurantModels
import com.vgomez.app.data.indexDatabase.Operation
import com.vgomez.app.data.indexDatabase.Model
import com.vgomez.app.data.indexDatabase.Response.{GetRestaurantModelsResponse, GetSequenceReviewModelsStarsResponse}

object ReaderFilterByCategories {
  // commands
  object Command {
    // Recommendations Categories
    case class GetRecommendationFilterByFavoriteCategories(favoriteCategories: Set[String], pageNumber: Long,
                                                           numberOfElementPerPage: Long)
    case class GetRecommendationFilterByUserFavoriteCategories(username: String, pageNumber: Long,
                                                               numberOfElementPerPage: Long)
  }

  def props(system: ActorSystem): Props =  Props(new ReaderFilterByCategories(system))
}

class ReaderFilterByCategories(system: ActorSystem) extends Actor with ActorLogging with Stash {
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
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationFilterByUserFavoriteCategories(sender(), pageNumber,
                                                                                            numberOfElementPerPage))

    case _ =>
      stash()
  }
  
  def getRestaurantsState(originalSender: ActorRef): Receive = {
    /*
    Todo #3
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
  Todo #5
    Description: Decouple Actor eliminate halfway methods.
    Action: Replace halfway for others actors.
    Status: No started
    Reported by: Sebastian Oliveri.
  */
  def halfwayGetRecommendationFilterByUserFavoriteCategories(originalSender: ActorRef, pageNumber: Long,
                                                             numberOfElementPerPage: Long): Receive = {
    case GetUserResponse(Some(userState)) =>
      userState match {
        case RegisterUserState(_, _, _, _, _, favoriteCategories) =>
          Operation.getRestaurantsModelByCategories(favoriteCategories.toList, pageNumber,
            numberOfElementPerPage).mapTo[GetRestaurantModelsResponse].pipeTo(self)
          unstashAll()
          context.become(getRestaurantsState(originalSender))

        case UnregisterUserState =>
          originalSender ! GetRecommendationResponse(None)
          unstashAll()
          context.become(state())
      }

    case GetUserResponse(None) =>
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}

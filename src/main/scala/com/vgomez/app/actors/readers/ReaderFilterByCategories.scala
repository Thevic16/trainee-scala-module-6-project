package com.vgomez.app.actors.readers


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse
import com.vgomez.app.actors.readers.ReaderUtility.getListRestaurantResponsesBySeqRestaurantModels
import com.vgomez.app.data.database.Operation
import com.vgomez.app.data.database.Model
import com.vgomez.app.data.database.Model.RestaurantModel
import com.vgomez.app.data.database.Response.{GetRestaurantModelsResponse, GetReviewModelsStarsResponse, GetSequenceReviewModelsStarsResponse}


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
      context.become(getAllRestaurantState(sender()))

    case GetRecommendationFilterByUserFavoriteCategories(username, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByUserFavoriteCategories command.")
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationFilterByUserFavoriteCategories(sender(), pageNumber,
                                                                                            numberOfElementPerPage))

    case _ =>
      stash()
  }
  
  def getAllRestaurantState(originalSender: ActorRef,
                            restaurantModels: Seq[Model.RestaurantModel] = Seq()): Receive = {

    case GetRestaurantModelsResponse(restaurantModels) =>
      if(restaurantModels.nonEmpty){
        val seqRestaurantId = restaurantModels.map(model => model.id)
        Operation.getReviewsStarsByListRestaurantId(seqRestaurantId).mapTo[GetSequenceReviewModelsStarsResponse].pipeTo(self)
        context.become(getAllRestaurantState(originalSender, restaurantModels))
      }
      else{
        originalSender ! GetRecommendationResponse(None)
        unstashAll()
        context.become(state())
      }

    case GetSequenceReviewModelsStarsResponse(seqReviewModelsStars) =>
      originalSender ! GetRecommendationResponse(Some(getListRestaurantResponsesBySeqRestaurantModels(restaurantModels,
                                                      seqReviewModelsStars)))
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }


  def halfwayGetRecommendationFilterByUserFavoriteCategories(originalSender: ActorRef, pageNumber: Long,
                                                             numberOfElementPerPage: Long): Receive = {
    case GetUserResponse(Some(userState)) =>
      Operation.getRestaurantsModelByCategories(userState.favoriteCategories.toList, pageNumber,
                                                 numberOfElementPerPage).mapTo[GetRestaurantModelsResponse].pipeTo(self)

      unstashAll()
      context.become(getAllRestaurantState(originalSender))

    case GetUserResponse(None) =>
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}

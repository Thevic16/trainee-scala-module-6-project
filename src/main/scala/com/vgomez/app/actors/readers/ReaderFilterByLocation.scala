package com.vgomez.app.actors.readers

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.{RegisterUserState, UnregisterUserState}
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.messages.AbstractMessage.Response.GetRecommendationResponse
import com.vgomez.app.domain.DomainModel.Location
import com.vgomez.app.actors.readers.ReaderUtility.getListRestaurantResponsesBySeqRestaurantModels
import com.vgomez.app.data.indexDatabase.Model.RestaurantModel
import com.vgomez.app.data.indexDatabase.Operation
import com.vgomez.app.data.indexDatabase.Response.{GetRestaurantModelsResponse, GetSequenceReviewModelsStarsResponse}
import com.vgomez.app.domain.DomainModelOperation.calculateDistanceInKm


object ReaderFilterByLocation {

  // commands
  object Command {
    // Recommendations Location
    case class GetRecommendationCloseToLocation(location: Location, rangeInKm: Double, pageNumber: Long,
                                                numberOfElementPerPage: Long)

    case class GetRecommendationCloseToMe(username: String, rangeInKm: Double, pageNumber: Long,
                                          numberOfElementPerPage: Long)
  }

  def props(system: ActorSystem): Props =  Props(new ReaderFilterByLocation(system))

}

class ReaderFilterByLocation(system: ActorSystem) extends Actor with ActorLogging with Stash {

  import ReaderFilterByLocation._
  import Command._
  import system.dispatcher

  def state(): Receive = {
    case GetRecommendationCloseToLocation(location, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToLocation command.")
      Operation.getPosiblesRestaurantsModelByLocation(location.latitude, location.longitude, rangeInKm, pageNumber,
        numberOfElementPerPage).mapTo[GetRestaurantModelsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurantState(sender(), location, rangeInKm))

    case GetRecommendationCloseToMe(username, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToMe command.")
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationCloseToMe(sender(), rangeInKm, pageNumber, numberOfElementPerPage))

    case _ =>
      stash()
  }

  def getAllRestaurantState(originalSender: ActorRef, queryLocation: Location, rangeInKm: Double,
                            restaurantModels: Seq[RestaurantModel] = Seq()): Receive = {

    case GetRestaurantModelsResponse(restaurantModels) =>
      val seqRestaurantId = restaurantModels.filter(model => calculateDistanceInKm(Location(model.latitude,
        model.longitude), Some(queryLocation)) <= rangeInKm).map(model => model.id)

      if (seqRestaurantId.nonEmpty) {
        Operation.getReviewsStarsByListRestaurantId(
                                               seqRestaurantId).mapTo[GetSequenceReviewModelsStarsResponse].pipeTo(self)
        context.become(getAllRestaurantState(originalSender, queryLocation, rangeInKm, restaurantModels))
      }
      else {
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

  def halfwayGetRecommendationCloseToMe(originalSender: ActorRef, rangeInKm: Double, pageNumber: Long,
                                        numberOfElementPerPage: Long): Receive = {
    case GetUserResponse(Some(userState)) =>
      userState match {
        case RegisterUserState(_, _, _, _, location, _) =>
          Operation.getPosiblesRestaurantsModelByLocation(location.latitude, location.longitude,
            rangeInKm, pageNumber, numberOfElementPerPage).mapTo[GetRestaurantModelsResponse].pipeTo(self)

          unstashAll()
          context.become(getAllRestaurantState(originalSender,
            Location(location.latitude, location.longitude), rangeInKm))

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

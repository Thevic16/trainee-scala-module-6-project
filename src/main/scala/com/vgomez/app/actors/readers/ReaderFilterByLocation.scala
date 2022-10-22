package com.vgomez.app.actors.readers

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse
import com.vgomez.app.domain.DomainModel.Location
import com.vgomez.app.actors.readers.ReaderUtility.getListRestaurantResponsesBySeqRestaurantModels
import com.vgomez.app.data.database.Model.RestaurantModel
import com.vgomez.app.data.database.Operation


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
        numberOfElementPerPage).mapTo[Seq[RestaurantModel]].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurantState(sender()))

    case GetRecommendationCloseToMe(username, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToMe command.")
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationCloseToMe(sender(), rangeInKm, pageNumber, numberOfElementPerPage))

    case _ =>
      stash()
  }

  def getAllRestaurantState(originalSender: ActorRef, restaurantModels: Seq[RestaurantModel] = Seq()): Receive = {

    case restaurantModels: Seq[RestaurantModel] =>
      if (restaurantModels.nonEmpty) {
        val seqRestaurantId = restaurantModels.map(model => model.id)
        Operation.getReviewsStarsByListRestaurantId(seqRestaurantId).mapTo[Seq[Seq[Int]]].pipeTo(self)
        context.become(getAllRestaurantState(originalSender, restaurantModels))
      }
      else {
        originalSender ! GetRecommendationResponse(None)
        unstashAll()
        context.become(state())
      }

    case reviewsStars: Seq[Seq[Int]] =>
      originalSender ! GetRecommendationResponse(Some(getListRestaurantResponsesBySeqRestaurantModels(restaurantModels,
        reviewsStars)))
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  def halfwayGetRecommendationCloseToMe(originalSender: ActorRef, rangeInKm: Double, pageNumber: Long,
                                        numberOfElementPerPage: Long): Receive = {
    case GetUserResponse(Some(userState)) =>
      Operation.getPosiblesRestaurantsModelByLocation(userState.location.latitude, userState.location.longitude,
        rangeInKm, pageNumber, numberOfElementPerPage).mapTo[Seq[RestaurantModel]].pipeTo(self)

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

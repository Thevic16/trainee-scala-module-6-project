package com.vgomez.app.actors.readers

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.intermediate.IntermediateReadUserAttributes.Command.GetUserLocation
import com.vgomez.app.domain.DomainModel.Location
import com.vgomez.app.data.projectionDatabase.Operation
import com.vgomez.app.data.projectionDatabase.Response.GetRestaurantModelsResponse
import com.vgomez.app.actors.readers.ReaderUtility.getListRestaurantStateBySeqRestaurantModels
import com.vgomez.app.domain.DomainModelOperation.calculateDistanceInKm

/*
Todo #R
  Description: Remove responses classes from actors.
  Action: Remove response class from ReaderFilterByLocation Actor.
  Status: Done
  Reported by: Sebastian Oliveri.
*/
object ReaderFilterByLocation {

  // commands
  object Command {
    // Recommendations Location
    case class GetRecommendationCloseToLocation(location: Location, rangeInKm: Double, pageNumber: Long,
                                                numberOfElementPerPage: Long)

    case class GetRecommendationCloseToMe(username: String, rangeInKm: Double, pageNumber: Long,
                                          numberOfElementPerPage: Long)
  }

  def props(system: ActorSystem,
            intermediateReadUserAttributes: ActorRef): Props =  Props(new ReaderFilterByLocation(system,
                                                                                        intermediateReadUserAttributes))

}

class ReaderFilterByLocation(system: ActorSystem,
                             intermediateReadUserAttributes: ActorRef) extends Actor with ActorLogging with Stash {

  import ReaderFilterByLocation._
  import Command._
  import system.dispatcher

  def state(): Receive = {
    case GetRecommendationCloseToLocation(location, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToLocation command.")
      Operation.getPosiblesRestaurantsModelByLocation(location.latitude, location.longitude, rangeInKm, pageNumber,
        numberOfElementPerPage).mapTo[GetRestaurantModelsResponse].pipeTo(self)
      unstashAll()
      context.become(getRestaurantsState(sender(), location, rangeInKm))

    case GetRecommendationCloseToMe(username, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToMe command.")
      intermediateReadUserAttributes ! GetUserLocation(username)
      unstashAll()
      context.become(intermediateGetUserLocationState(sender(), rangeInKm, pageNumber, numberOfElementPerPage))

    case _ =>
      stash()
  }

  def getRestaurantsState(originalSender: ActorRef, queryLocation: Location, rangeInKm: Double): Receive = {
    /*
    Todo #1
      Description: Decouple restaurant.
      Action: Remove stars request on the database and only left restaurant models.
      Status: Done
      Reported by: Sebastian Oliveri.
    */
    case GetRestaurantModelsResponse(restaurantModels) =>
      val restaurantModelsFilterByDistance = restaurantModels.filter(model =>
        calculateDistanceInKm(Location(model.latitude, model.longitude), Some(queryLocation)) <= rangeInKm)

      if (restaurantModelsFilterByDistance.nonEmpty)
        originalSender ! Some(getListRestaurantStateBySeqRestaurantModels(restaurantModelsFilterByDistance))
      else originalSender ! None

      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  /*
  Todo #5
    Description: Decouple Actor eliminate halfway methods.
    Action: Let the responsibility to get user location to other actor.
    Status: Done
    Reported by: Sebastian Oliveri.
  */
  def intermediateGetUserLocationState(originalSender: ActorRef, rangeInKm: Double, pageNumber: Long,
                                        numberOfElementPerPage: Long): Receive = {
    case Some(userLocation: Location) =>
      Operation.getPosiblesRestaurantsModelByLocation(userLocation.latitude, userLocation.longitude,
        rangeInKm, pageNumber, numberOfElementPerPage).mapTo[GetRestaurantModelsResponse].pipeTo(self)

      unstashAll()
      context.become(getRestaurantsState(originalSender,
        Location(userLocation.latitude, userLocation.longitude), rangeInKm))

    case None =>
      originalSender ! None
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}

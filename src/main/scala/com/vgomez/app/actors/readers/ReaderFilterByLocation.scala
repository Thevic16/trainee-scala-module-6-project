package com.vgomez.app.actors.readers

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.{Flow, RunnableGraph, Source}
import com.vgomez.app.actors.Restaurant.Command.GetRestaurant
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.getMaterializeGraphReaderUtility
import com.vgomez.app.domain.DomainModel.Location
import com.vgomez.app.domain.DomainModelOperation.calculateDistanceInKm
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.Response._

import scala.concurrent.Future

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

  def getGraphQueryReader(eventsWithSequenceSource: Source[EventEnvelope,
                                    NotUsed]): RunnableGraph[Future[Seq[String]]] = {
    import com.vgomez.app.actors.readers.ReaderGetAll.RestaurantCreated

    val flowMapGetIdFromEvent = Flow[Any].map {
      case RestaurantCreated(id) => id
      case _ => ""
    }
    getMaterializeGraphReaderUtility(eventsWithSequenceSource, flowMapGetIdFromEvent)
  }

}

class ReaderFilterByLocation(system: ActorSystem) extends ActorLogging with Stash {

  import ReaderFilterByLocation._
  import Command._
  import system.dispatcher

  // ReaderDatabaseUtility
  val readerDatabaseUtility = ReaderDatabaseUtility(system)

  def state(): Receive = {
    case GetRecommendationCloseToLocation(location, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToLocation command.")
      getEventsIdsRestaurantType().mapTo[GetEventsIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurantState(sender(),location, rangeInKm, Int.MaxValue, pageNumber, numberOfElementPerPage))

    case GetRecommendationCloseToMe(username, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToMe command.")
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationCloseToMe(sender(), rangeInKm, pageNumber, numberOfElementPerPage))

    case _ =>
      stash()
  }

  def getAllRestaurantState(originalSender: ActorRef, queryLocation: Location, rangeInKm: Double,
                       totalAmountId: Int, pageNumber: Long, numberOfElementPerPage: Long, currentAmountId: Int = 0,
                       accResponses: List[GetRestaurantResponse] = List()): Receive = {

    case GetEventsIdsResponse(ids) =>
      processGetEventsIdsResponseCommand(originalSender, queryLocation, rangeInKm, pageNumber, numberOfElementPerPage,
                                          ids)

    case getResponse@GetRestaurantResponse(Some(restaurantState), Some(_)) =>
      processGetResponseCommand(originalSender, queryLocation, rangeInKm, totalAmountId, pageNumber,
                                numberOfElementPerPage, currentAmountId, accResponses, getResponse,
                                restaurantLocation = Some(restaurantState.location), none = false)

    case getResponse@GetRestaurantResponse(None, None) =>
      processGetResponseCommand(originalSender, queryLocation, rangeInKm, totalAmountId, pageNumber,
        numberOfElementPerPage, currentAmountId, accResponses, getResponse,
        restaurantLocation = None, none = true)

    case _ =>
      stash()
  }

  def halfwayGetRecommendationCloseToMe(originalSender: ActorRef, rangeInKm: Double, pageNumber: Long,
                                        numberOfElementPerPage: Long): Receive = {
    case GetUserResponse(Some(userState)) =>
      getEventsIdsRestaurantType().mapTo[GetEventsIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurantState(originalSender, userState.location, rangeInKm, Int.MaxValue, pageNumber,
                                            numberOfElementPerPage))

    case GetUserResponse(None) =>
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()

  // Auxiliary methods ReaderDatabaseUtility
  def getEventsIdsRestaurantType(): Future[GetEventsIdsResponse] = {
    import com.vgomez.app.actors.readers.ReaderGetAll.ActorType.restaurantType

    val eventsWithSequenceSource = readerDatabaseUtility.getSourceEventSByTag(restaurantType)
    val graph: RunnableGraph[Future[Seq[String]]] = getGraphQueryReader(eventsWithSequenceSource)
    readerDatabaseUtility.runGraph(graph)
  }

  // Auxiliary methods getRestaurantState
  def processGetEventsIdsResponseCommand(originalSender: ActorRef, queryLocation: Location, rangeInKm: Double,
                                         pageNumber: Long, numberOfElementPerPage: Long, ids: Set[String]): Unit = {
    val currentAmountId: Int = 0
    val accResponses = List()

    if (ids.nonEmpty) {
      log.info("getting ids")
      ids.foreach(id => context.parent ! GetRestaurant(id))
      context.become(getAllRestaurantState(originalSender, queryLocation, rangeInKm, ids.size, pageNumber,
                                            numberOfElementPerPage, currentAmountId, accResponses))
    }
    else {
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state())
    }
  }

  def processGetResponseCommand(originalSender: ActorRef,
                                queryLocation: Location, rangeInKm: Double, totalAmountId: Int, pageNumber: Long,
                                numberOfElementPerPage: Long, currentAmountId: Int,
                                accResponses: List[GetRestaurantResponse], getResponse: GetRestaurantResponse,
                                restaurantLocation: Option[Location],
                                none: Boolean): Unit = {
    log.info("receiving GetResponse from administration.")

    if (currentAmountId + 1 >= totalAmountId) {
      log.info(s"finishing currentAmountId: ${currentAmountId + 1} of total: $totalAmountId.")
      if (!none && calculateDistanceInKm(queryLocation, restaurantLocation) <= rangeInKm)
        {
          val responses = (accResponses :+ getResponse).slice((pageNumber * numberOfElementPerPage).toInt,
                                             (pageNumber * numberOfElementPerPage).toInt + numberOfElementPerPage.toInt)
          originalSender ! GetRecommendationResponse(Some(responses))
        }
      else
        {
          val responses = accResponses.slice((pageNumber * numberOfElementPerPage).toInt,
                                             (pageNumber * numberOfElementPerPage).toInt + numberOfElementPerPage.toInt)
          originalSender ! GetRecommendationResponse(Some(responses))
        }

      unstashAll()
      context.become(state())
    }
    else {
      log.info(s"becoming currentAmountId: $currentAmountId of total: $totalAmountId.")
      if (!none && calculateDistanceInKm(queryLocation, restaurantLocation) <= rangeInKm)
        context.become(getAllRestaurantState(originalSender, queryLocation, rangeInKm,
                                              totalAmountId, pageNumber, numberOfElementPerPage, currentAmountId + 1,
                                               accResponses :+ getResponse))
      else
        context.become(getAllRestaurantState(originalSender, queryLocation, rangeInKm,
          totalAmountId, pageNumber, numberOfElementPerPage, currentAmountId + 1,
          accResponses))
    }
  }
}

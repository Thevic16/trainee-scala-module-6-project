package com.vgomez.app.actors.readers

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery, Sequence}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import com.vgomez.app.actors.Restaurant.Command.GetRestaurant
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.getGraphReaderUtility
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
    getGraphReaderUtility(eventsWithSequenceSource, flowMapGetIdFromEvent)
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
      context.become(getAllRestaurant(sender(),location, rangeInKm, Int.MaxValue, pageNumber, numberOfElementPerPage))

    case GetRecommendationCloseToMe(username, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToMe command.")
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationCloseToMe(sender(), rangeInKm, pageNumber, numberOfElementPerPage))

    case _ =>
      stash()
  }

  def getAllRestaurant(originalSender: ActorRef, queryLocation: Location, rangeInKm: Double,
                       totalAmountId: Int, pageNumber: Long, numberOfElementPerPage: Long, currentAmountId: Int = 0,
                       accResponses: List[GetRestaurantResponse] = List()): Receive = {

    case GetEventsIdsResponse(ids) =>
      if(ids.nonEmpty){
        log.info("getAllRestaurant getting ids restaurant.")

        ids.foreach(id => context.parent ! GetRestaurant(id))
        context.become(getAllRestaurant(originalSender, queryLocation, rangeInKm, ids.size, pageNumber, numberOfElementPerPage, currentAmountId, accResponses))
      }
      else {
        originalSender ! GetRecommendationResponse(None)

        unstashAll()
        context.become(state())
      }

    case getResponse@GetRestaurantResponse(Some(restaurantState), _) =>
      log.info("getAllRestaurants receiving GetRestaurantResponse from administration.")

      if(currentAmountId+1 >= totalAmountId) {
        log.info(s"getAllRestaurants finishing currentAmountId: ${currentAmountId+1} of total: $totalAmountId.")

        if(calculateDistanceInKm(queryLocation, restaurantState.location) <= rangeInKm){
          val responses = (accResponses :+ getResponse).drop((pageNumber * numberOfElementPerPage).toInt).take(numberOfElementPerPage.toInt)

          if(responses.nonEmpty)
            originalSender ! GetRecommendationResponse(Some(responses))
          else originalSender ! GetRecommendationResponse(None)

        }
        else {
          val responses = accResponses.drop((pageNumber * numberOfElementPerPage).toInt).take(numberOfElementPerPage.toInt)

          if (responses.nonEmpty)
            originalSender ! GetRecommendationResponse(Some(responses))
          else originalSender ! GetRecommendationResponse(None)

        }
        unstashAll()
        context.become(state())
      }
      else {
        log.info(s"getAllRestaurants becoming currentAmountId: $currentAmountId of total: $totalAmountId.")

        if(calculateDistanceInKm(queryLocation, restaurantState.location) <= rangeInKm){
          context.become(getAllRestaurant(originalSender, queryLocation, rangeInKm, totalAmountId, pageNumber, numberOfElementPerPage, currentAmountId + 1,
                                          accResponses :+ getResponse))
        }
        else {
          context.become(getAllRestaurant(originalSender, queryLocation, rangeInKm, totalAmountId, pageNumber, numberOfElementPerPage, currentAmountId + 1,
                                          accResponses))
        }
      }
    case GetRestaurantResponse(None, None) =>
      context.become(getAllRestaurant(originalSender, queryLocation, rangeInKm, totalAmountId, pageNumber, numberOfElementPerPage, currentAmountId + 1,
                      accResponses))
    case _ =>
      stash()
  }


  def halfwayGetRecommendationCloseToMe(originalSender: ActorRef, rangeInKm: Double, pageNumber: Long, numberOfElementPerPage: Long): Receive = {
    case GetUserResponse(Some(userState)) =>
      getEventsIdsRestaurantType().mapTo[GetEventsIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurant(originalSender, userState.location, rangeInKm, Int.MaxValue, pageNumber, numberOfElementPerPage))

    case GetUserResponse(None) =>
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()

  // Auxiliary methods
  def getEventsIdsRestaurantType(): Future[GetEventsIdsResponse] = {
    import com.vgomez.app.actors.readers.ReaderGetAll.ActorType.restaurantType

    val eventsWithSequenceSource = readerDatabaseUtility.getSourceEventSByTag(restaurantType)
    val graph: RunnableGraph[Future[Seq[String]]] = getGraphQueryReader(eventsWithSequenceSource)
    readerDatabaseUtility.runGraph(graph)
  }
}

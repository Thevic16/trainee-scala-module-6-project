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
import com.vgomez.app.domain.DomainModel.Location
import com.vgomez.app.domain.DomainModelOperation.calculateDistanceInKm

import scala.concurrent.Future

object ReaderFilterByLocation {

  // commands
  object Command {
    // Recommendations Location
    case class GetRecommendationCloseToLocation(location: Location, rangeInKm: Double)

    case class GetRecommendationCloseToMe(username: String, rangeInKm: Double)
  }

  object Response {
    case class GetAllIdsResponse(ids : Set[String])
  }

  def props(system: ActorSystem): Props =  Props(new ReaderFilterByLocation(system))

  import Response.GetAllIdsResponse
  def getAllIdsRestaurant(system: ActorSystem): Future[GetAllIdsResponse] = {
    import system.dispatcher
    import com.vgomez.app.actors.readers.ReaderGetAll.ActorType.restaurantType

    val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
    implicit val materializer = ActorMaterializer()(system)

    val eventsWithSequenceSource = queries.currentEventsByTag(tag = restaurantType, offset = Sequence(0L))
    val graph: RunnableGraph[Future[Seq[String]]] = getGraph(eventsWithSequenceSource)

    graph.run().map(seqIds => GetAllIdsResponse(seqIds.toSet))
  }

  def getGraph(eventsWithSequenceSource: Source[EventEnvelope, NotUsed]): RunnableGraph[Future[Seq[String]]] = {
    import com.vgomez.app.actors.readers.ReaderGetAll.RestaurantCreated

    val eventsSource = eventsWithSequenceSource.map(_.event)
    val flow = Flow[Any].map {
      case RestaurantCreated(id) => id
      case _ => ""
    }
    val sink = Sink.seq[String]

    eventsSource.via(flow).toMat(sink)(Keep.right)
  }

}

class ReaderFilterByLocation(system: ActorSystem) extends ActorLogging with Stash {

  import ReaderFilterByLocation._
  import Command._
  import Response._
  import system.dispatcher


  def state(): Receive = {
    case GetRecommendationCloseToLocation(location, rangeInKm) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToLocation command.")
      getAllIdsRestaurant(system).mapTo[GetAllIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurant(sender(),location, rangeInKm, Int.MaxValue))

    case GetRecommendationCloseToMe(username, rangeInKm) =>
      log.info("ReaderFilterByLocation has receive a GetRecommendationCloseToMe command.")
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationCloseToMe(sender(), rangeInKm))

    case _ =>
      stash()
  }

  def getAllRestaurant(originalSender: ActorRef, queryLocation: Location, rangeInKm: Double,
                       totalAmountId: Int, currentAmountId: Int = 0,
                       accResponses: List[GetRestaurantResponse] = List()): Receive = {

    case GetAllIdsResponse(ids) =>
      if(ids.nonEmpty){
        log.info("getAllRestaurant getting ids restaurant.")

        ids.foreach(id => context.parent ! GetRestaurant(id))
        context.become(getAllRestaurant(originalSender, queryLocation, rangeInKm, ids.size, currentAmountId, accResponses))
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
          originalSender ! GetRecommendationResponse(Some(accResponses :+ getResponse))
        }
        else originalSender ! GetRecommendationResponse(Some(accResponses))

        unstashAll()
        context.become(state())
      }
      else {
        log.info(s"getAllRestaurants becoming currentAmountId: $currentAmountId of total: $totalAmountId.")

        if(calculateDistanceInKm(queryLocation, restaurantState.location) <= rangeInKm){
          context.become(getAllRestaurant(originalSender, queryLocation, rangeInKm, totalAmountId, currentAmountId + 1,
                                          accResponses :+ getResponse))
        }
        else {
          context.become(getAllRestaurant(originalSender, queryLocation, rangeInKm, totalAmountId, currentAmountId + 1,
                                          accResponses))
        }
      }
    case GetRestaurantResponse(None, None) =>
      context.become(getAllRestaurant(originalSender, queryLocation, rangeInKm, totalAmountId, currentAmountId + 1,
                      accResponses))
    case _ =>
      stash()
  }


  def halfwayGetRecommendationCloseToMe(originalSender: ActorRef, rangeInKm: Double): Receive = {
    case GetUserResponse(Some(userState)) =>
      getAllIdsRestaurant(system).mapTo[GetAllIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurant(originalSender, userState.location, rangeInKm, Int.MaxValue))

    case GetUserResponse(None) =>
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}
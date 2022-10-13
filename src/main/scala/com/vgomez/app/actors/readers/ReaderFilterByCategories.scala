package com.vgomez.app.actors.readers

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import akka.persistence.PersistentActor
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.{Flow, RunnableGraph, Source}
import com.vgomez.app.actors.Restaurant.Command.GetRestaurant
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Event.Event
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.Response._
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.getGraphReaderUtility
import com.vgomez.app.domain.DomainModelOperation.restaurantCategoriesIsContainsByQueryCategories

import scala.concurrent.Future

object ReaderFilterByCategories {
  // state
  case class ReaderFilterByCategoriesState(restaurants: Set[String])

  // commands
  object Command {
    case class CreateRestaurant(id: String, categories: Set[String])
    case class UpdateRestaurant(id: String, categories: Set[String])

    // Recommendations Categories
    case class GetRecommendationFilterByFavoriteCategories(favoriteCategories: Set[String], pageNumber: Long,
                                                           numberOfElementPerPage: Long)
    case class GetRecommendationFilterByUserFavoriteCategories(username: String, pageNumber: Long,
                                                               numberOfElementPerPage: Long)
  }

  // events
  case class RestaurantCreated(id: String, categories: Set[String]) extends Event
  case class RestaurantUpdated(id: String, categories: Set[String]) extends Event

  def props(system: ActorSystem): Props =  Props(new ReaderFilterByCategories(system))

  // WriteEventAdapter
  class ReaderFilterByCategoriesEventAdapter extends WriteEventAdapter {
    override def toJournal(event: Any): Any = event match {
      case RestaurantCreated(_, categories) =>
        Tagged(event, categories)
      case RestaurantUpdated(_, categories) =>
        Tagged(event, categories)
      case _ =>
        event
    }
    override def manifest(event: Any): String = ""
  }

  def getGraphQueryReader(eventsWithSequenceSource: Source[EventEnvelope,
    NotUsed]): RunnableGraph[Future[Seq[String]]] = {
    val flowMapGetIdFromEvent = Flow[Any].map {
      case RestaurantCreated(id, _) => id
      case RestaurantUpdated(id, _) => id
      case _ => ""
    }
    getGraphReaderUtility(eventsWithSequenceSource, flowMapGetIdFromEvent)
  }
}

class ReaderFilterByCategories(system: ActorSystem) extends PersistentActor with ActorLogging with Stash {
  import ReaderFilterByCategories._
  import Command._
  import system.dispatcher

  // ReaderDatabaseUtility
  val readerDatabaseUtility = ReaderDatabaseUtility(system)

  // state
  var readerFilterByCategoriesRecoveryState = ReaderFilterByCategoriesState(Set())

  override def persistenceId: String = "reader-filter-by-categories"

  def state(readerFilterByCategoriesState: ReaderFilterByCategoriesState): Receive = {
    case CreateRestaurant(id, categories) =>
      val newState: ReaderFilterByCategoriesState = readerFilterByCategoriesState.copy(
                                                          restaurants = readerFilterByCategoriesState.restaurants + id)
      persist(RestaurantCreated(id, categories)) { _ =>
        log.info(s"ReaderFilterByCategories create a restaurant with id: $id")
        context.become(state(newState))
      }

    case UpdateRestaurant(id, categories) =>
      persist(RestaurantUpdated(id, categories)) { _ =>
        log.info(s"ReaderFilterByCategories update a restaurant with id: $id")
      }

    case GetRecommendationFilterByFavoriteCategories(favoriteCategories, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByFavoriteCategories command.")
      getEventsIdsByCategories(favoriteCategories, pageNumber, numberOfElementPerPage).mapTo[GetEventsIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurantState(readerFilterByCategoriesState, sender(),favoriteCategories, Int.MaxValue))

    case GetRecommendationFilterByUserFavoriteCategories(username, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByUserFavoriteCategories command.")
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationFilterByUserFavoriteCategories(readerFilterByCategoriesRecoveryState,
                                                                            sender(), pageNumber, numberOfElementPerPage))

    case _ =>
      stash()
  }
  
  def getAllRestaurantState(readerFilterByCategoriesState: ReaderFilterByCategoriesState, originalSender: ActorRef,
                       queryCategories: Set[String], totalAmountId: Int, currentAmountId: Int = 0,
                       accResponses: List[GetRestaurantResponse] = List()): Receive = {

    case GetEventsIdsResponse(ids) =>
      processGetEventsIdsResponseCommand(readerFilterByCategoriesState, originalSender, queryCategories, ids)

    case getResponse@GetRestaurantResponse(Some(restaurantState), Some(_)) =>
      processGetResponseCommand(readerFilterByCategoriesState, originalSender, queryCategories, totalAmountId,
                                currentAmountId, accResponses, getResponse,
                                restaurantCategories = restaurantState.categories, none = false)

    case getResponse@GetRestaurantResponse(None, None) =>
      processGetResponseCommand(readerFilterByCategoriesState, originalSender, queryCategories, totalAmountId,
        currentAmountId, accResponses, getResponse, restaurantCategories = Set(),  none = true)

    case _ =>
      stash()
  }


  def halfwayGetRecommendationFilterByUserFavoriteCategories(readerFilterByCategoriesState: ReaderFilterByCategoriesState,
                                                             originalSender: ActorRef, pageNumber: Long,
                                                             numberOfElementPerPage: Long): Receive = {
    case GetUserResponse(Some(userState)) =>
      getEventsIdsByCategories(userState.favoriteCategories, pageNumber,
                                numberOfElementPerPage).mapTo[GetEventsIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurantState(readerFilterByCategoriesState, originalSender, userState.favoriteCategories,
                                      Int.MaxValue))

    case GetUserResponse(None) =>
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state(readerFilterByCategoriesRecoveryState))

    case _ =>
      stash()
  }

  override def receiveCommand: Receive = state(ReaderFilterByCategoriesState(Set()))

  override def receiveRecover: Receive = {
    case RestaurantCreated(id, _) =>
      log.info(s"ReaderFilterByCategories has recovered a restaurant with id: $id")
      readerFilterByCategoriesRecoveryState = readerFilterByCategoriesRecoveryState.copy(
        restaurants = readerFilterByCategoriesRecoveryState.restaurants + id)

      context.become(state(readerFilterByCategoriesRecoveryState))

    case RestaurantUpdated(id, _) => log.info(s"ReaderFilterByCategories has recovered a update " +
                                              s"for restaurant with id: $id")
  }

  // Auxiliary methods reader database.
  def getEventsIdsByCategories(categories: Set[String], pageNumber: Long,
                            numberOfElementPerPage: Long): Future[GetEventsIdsResponse] = {
    val eventsWithSequenceSource = readerDatabaseUtility.getSourceEventSByTagSet(categories)
    val graph: RunnableGraph[Future[Seq[String]]] = getGraphQueryReader(eventsWithSequenceSource)
    readerDatabaseUtility.runGraphWithPagination(graph, pageNumber, numberOfElementPerPage)
  }

  // Auxiliary methods getRestaurantState
  def processGetEventsIdsResponseCommand(readerFilterByCategoriesState: ReaderFilterByCategoriesState,
                                         originalSender: ActorRef, queryCategories: Set[String],
                                         ids: Set[String]): Unit = {
    val currentAmountId: Int = 0
    val accResponses = List()

    if (ids.nonEmpty) {
      log.info("getting ids")
      ids.foreach(id => context.parent ! GetRestaurant(id))
      context.become(getAllRestaurantState(readerFilterByCategoriesState, originalSender, queryCategories,  ids.size,
                                           currentAmountId, accResponses))
    }
    else {
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state(readerFilterByCategoriesState))
    }
  }

  def processGetResponseCommand(readerFilterByCategoriesState: ReaderFilterByCategoriesState, originalSender: ActorRef,
                                queryCategories: Set[String], totalAmountId: Int, currentAmountId: Int,
                                accResponses: List[GetRestaurantResponse],
                                getResponse: GetRestaurantResponse, restaurantCategories: Set[String],
                                none: Boolean): Unit = {
    log.info("receiving GetResponse from administration.")

    if (currentAmountId + 1 >= totalAmountId) {
      log.info(s"finishing currentAmountId: ${currentAmountId + 1} of total: $totalAmountId.")
      if (!none && restaurantCategoriesIsContainsByQueryCategories(restaurantCategories, queryCategories))
        originalSender ! GetRecommendationResponse(Some(accResponses :+ getResponse))
      else
        originalSender ! GetRecommendationResponse(Some(accResponses))

      unstashAll()
      context.become(state(readerFilterByCategoriesState))
    }
    else {
      log.info(s"becoming currentAmountId: $currentAmountId of total: $totalAmountId.")
      if (!none && restaurantCategoriesIsContainsByQueryCategories(restaurantCategories, queryCategories))
        context.become(getAllRestaurantState(readerFilterByCategoriesState, originalSender, queryCategories,
                        totalAmountId, currentAmountId + 1, accResponses :+ getResponse))
      else
        context.become(getAllRestaurantState(readerFilterByCategoriesState, originalSender, queryCategories,
                                              totalAmountId, currentAmountId + 1, accResponses))
    }
  }

}

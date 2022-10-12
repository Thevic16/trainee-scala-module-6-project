package com.vgomez.app.actors.readers

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import akka.persistence.PersistentActor
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery, Sequence}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Concat, Flow, Keep, Merge, RunnableGraph, Sink, Source}
import com.vgomez.app.actors.Restaurant.Command.GetRestaurant
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.Review.Command.GetReview
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Event.Event
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse

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

  object Response {
    case class GetAllIdsResponse(ids : Set[String])
  }

  // events
  case class RestaurantCreated(id: String, categories: Set[String]) extends Event
  case class RestaurantUpdated(id: String, categories: Set[String]) extends Event

  def props(system: ActorSystem): Props =  Props(new ReaderFilterByCategories(system))

  // WriteEventAdapter
  class ReaderFilterByCategoriesEventAdapter extends WriteEventAdapter {
    override def toJournal(event: Any): Any = event match {
      case RestaurantCreated(_, categories) =>
        println("Tagging RestaurantCreated event with categories.")
        Tagged(event, categories)
      case RestaurantUpdated(_, categories) =>
        println("Tagging RestaurantUpdated event with categories.")
        Tagged(event, categories)
      case _ =>
        event
    }
    override def manifest(event: Any): String = ""
  }

  import Response.GetAllIdsResponse
  def getAllIdsByCategories(categories: Set[String], system: ActorSystem, pageNumber: Long,
                            numberOfElementPerPage: Long): Future[GetAllIdsResponse] = {
    import system.dispatcher

    val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
    implicit val materializer = ActorMaterializer()(system)

    val listEventsWithSequenceSource = categories.toList.map(category => queries.currentEventsByTag(
                                                                tag = category, offset = Sequence(0L)))

    val eventsWithSequenceSource = listEventsWithSequenceSource.fold(Source.empty)(Source.combine(_,_)(Concat(_)))

    val eventsWithSequenceSourcePagination = eventsWithSequenceSource

    val graph: RunnableGraph[Future[Seq[String]]] = getGraph(eventsWithSequenceSourcePagination)

    graph.run().map(seqIds => GetAllIdsResponse(seqIds.toSet.drop((numberOfElementPerPage * pageNumber).toInt).take(numberOfElementPerPage.toInt)))
  }

  def getGraph(eventsWithSequenceSource: Source[EventEnvelope, NotUsed]): RunnableGraph[Future[Seq[String]]] = {
    val eventsSource = eventsWithSequenceSource.map(_.event)
    val flow = Flow[Any].map {
      case RestaurantCreated(id, _) => id
      case RestaurantUpdated(id, _) => id
      case _ => ""
    }
    val sink = Sink.seq[String]

    eventsSource.via(flow).toMat(sink)(Keep.right)
  }
}

class ReaderFilterByCategories(system: ActorSystem) extends PersistentActor with ActorLogging with Stash {

  import ReaderFilterByCategories._
  import Command._
  import Response._
  import system.dispatcher

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
      getAllIdsByCategories(favoriteCategories, system, pageNumber, numberOfElementPerPage).mapTo[GetAllIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurant(readerFilterByCategoriesState, sender(),favoriteCategories, Int.MaxValue))

    case GetRecommendationFilterByUserFavoriteCategories(username, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByUserFavoriteCategories command.")
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationFilterByUserFavoriteCategories(readerFilterByCategoriesRecoveryState,
                                                                            sender(), pageNumber, numberOfElementPerPage))

    case _ =>
      stash()
  }
  
  def getAllRestaurant(readerFilterByCategoriesState: ReaderFilterByCategoriesState, originalSender: ActorRef,
                       queryCategories: Set[String], totalAmountId: Int, currentAmountId: Int = 0,
                       accResponses: List[GetRestaurantResponse] = List()): Receive = {

    case GetAllIdsResponse(ids) =>
      if(ids.nonEmpty){
        log.info("getAllRestaurant getting ids restaurant.")
        ids.foreach(id => context.parent ! GetRestaurant(id))
        context.become(getAllRestaurant(readerFilterByCategoriesState, originalSender, queryCategories, ids.size, currentAmountId,
                                        accResponses))
      }
      else {
        originalSender ! GetRecommendationResponse(None)

        unstashAll()
        context.become(state(readerFilterByCategoriesState))
      }

    case getResponse@GetRestaurantResponse(Some(restaurantState), _) =>
      log.info("getAllRestaurants receiving GetRestaurantResponse from administration.")
      if(currentAmountId+1 >= totalAmountId) {
        log.info(s"getAllRestaurants finishing currentAmountId: ${currentAmountId+1} of total: $totalAmountId.")

        if(restaurantCategoriesIsContainsByQueryCategories(restaurantState.categories, queryCategories)){
          originalSender ! GetRecommendationResponse(Some(accResponses :+ getResponse))
        }
        else originalSender ! GetRecommendationResponse(Some(accResponses))

        unstashAll()
        context.become(state(readerFilterByCategoriesState))
      }
      else {
        log.info(s"getAllRestaurants becoming currentAmountId: $currentAmountId of total: $totalAmountId.")

        if(restaurantCategoriesIsContainsByQueryCategories(restaurantState.categories, queryCategories)){
          context.become(getAllRestaurant(readerFilterByCategoriesState, originalSender, queryCategories,
            totalAmountId, currentAmountId + 1, accResponses :+ getResponse))
        }
        else {
          context.become(getAllRestaurant(readerFilterByCategoriesState, originalSender, queryCategories,
            totalAmountId, currentAmountId + 1, accResponses))
        }
      }
    case GetRestaurantResponse(None, None) =>
      context.become(getAllRestaurant(readerFilterByCategoriesState, originalSender, queryCategories,
        totalAmountId, currentAmountId + 1, accResponses))
    case _ =>
      stash()
  }


  def halfwayGetRecommendationFilterByUserFavoriteCategories(readerFilterByCategoriesState: ReaderFilterByCategoriesState,
                                                             originalSender: ActorRef, pageNumber: Long,
                                                             numberOfElementPerPage: Long): Receive = {
    case GetUserResponse(Some(userState)) =>
      getAllIdsByCategories(userState.favoriteCategories, system, pageNumber, numberOfElementPerPage).mapTo[GetAllIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurant(readerFilterByCategoriesState, originalSender, userState.favoriteCategories,
                                      Int.MaxValue))

    case GetUserResponse(None) =>
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state(readerFilterByCategoriesRecoveryState))

    case _ =>
      stash()
  }

  def restaurantCategoriesIsContainsByQueryCategories(restaurantCategories: Set[String],
                                                      queryCategories: Set[String]): Boolean =
    {
      def go(restaurantCategories: Set[String]): Boolean = {
        if(restaurantCategories.isEmpty) false
        else if (queryCategories.contains(restaurantCategories.head)) true
        else go(restaurantCategories.tail)
      }

      go(restaurantCategories)
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

}

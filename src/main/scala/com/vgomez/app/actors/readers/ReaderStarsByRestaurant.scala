package com.vgomez.app.actors.readers

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import akka.persistence.PersistentActor
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery, Sequence}
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Keep, Merge, RunnableGraph, Sink, Source}
import com.vgomez.app.actors.Restaurant.Command.GetRestaurant
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Event.Event
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse

import scala.concurrent.Future

object ReaderStarsByRestaurant {
  // state
  case class ReaderStarsByRestaurantState(reviews: Set[String])

  // commands
  object Command {
    case class CreateReview(id: String, restaurantId: String, stars: Int)
    case class UpdateReview(id: String, restaurantId: String, stars: Int)
    case class GetStartByRestaurant(restaurantId: String)

  }

  object Response {
    case class GetAllStarsByRestaurantResponse(starsList : Seq[Int])
    case class GetStartByRestaurantResponse(starts: Int)
  }

  // events
  case class ReviewCreated(id: String, restaurantId: String, stars: Int) extends Event
  case class ReviewUpdated(id: String, restaurantId: String, stars: Int) extends Event

  def props(system: ActorSystem): Props =  Props(new ReaderStarsByRestaurant(system))

  // WriteEventAdapter
  class ReaderStarByRestaurantAdapter extends WriteEventAdapter {
    override def toJournal(event: Any): Any = event match {
      case ReviewCreated(_, restaurantId,_) =>
        Tagged(event, Set(restaurantId))
      case ReviewUpdated(_, restaurantId, _) =>
        Tagged(event, Set(restaurantId))
      case _ =>
        event
    }
    override def manifest(event: Any): String = ""
  }

  def runGraphQueryReader(eventsWithSequenceSource: Source[EventEnvelope, NotUsed], materializer: Materializer,
               queryRestaurantId: String): Future[Seq[Int]] = {
    val eventsSource = eventsWithSequenceSource.map(_.event)

    val flowFilter = Flow[Any].filter{
      case ReviewCreated(_, restaurantId, _) if restaurantId == queryRestaurantId => true
      case ReviewUpdated(_, restaurantId, _) if restaurantId == queryRestaurantId => true
      case _ => false
    }

    val flowMap = Flow[Any].map {
      case ReviewCreated(_, _, stars) => stars
      case ReviewUpdated(_, _, stars) => stars
      case _ => 0
    }

    val sink = Sink.seq[Int]
    eventsSource.via(flowFilter).via(flowMap).runWith(sink)(materializer)
  }

}

class ReaderStarsByRestaurant(system: ActorSystem) extends PersistentActor with ActorLogging with Stash {

  import ReaderStarsByRestaurant._
  import Command._
  import Response._
  import system.dispatcher

  // ReaderDatabaseUtility
  val readerDatabaseUtility = ReaderDatabaseUtility(system)

  // state
  var readerStarsByRestaurantRecoveryState = ReaderStarsByRestaurantState(Set())

  override def persistenceId: String = "reader-starts-by-restaurant"

  def state(readerStarsByRestaurantState: ReaderStarsByRestaurantState): Receive = {
    case CreateReview(id, restaurantId, stars) =>
      val newState: ReaderStarsByRestaurantState = readerStarsByRestaurantState.copy(
                                                          reviews = readerStarsByRestaurantState.reviews + id)
      persist(ReviewCreated(id, restaurantId, stars)) { _ =>
        log.info(s"ReaderStarsByRestaurant create a review with id: $id")
        context.become(state(newState))
      }

    case UpdateReview(id, categories, stars) =>
      persist(ReviewUpdated(id, categories, stars)) { _ =>
        log.info(s"ReaderStarsByRestaurant update a review with id: $id")
      }

    case GetStartByRestaurant(restaurantId) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByFavoriteCategories command.")
      getAllStarsByRestaurant(restaurantId).mapTo[GetAllStarsByRestaurantResponse].pipeTo(self)
      unstashAll()
      context.become(getStartByRestaurant(readerStarsByRestaurantState, sender()))

    case _ =>
      stash()
  }
  
  def getStartByRestaurant(readerStarsByRestaurantState: ReaderStarsByRestaurantState, originalSender: ActorRef): Receive = {

    case GetAllStarsByRestaurantResponse(starsList) =>
      log.info("getStartByRestaurant getting startsList.")
      if(starsList.nonEmpty){
        val startAVG: Int = starsList.sum / starsList.length
        originalSender ! GetStartByRestaurantResponse(startAVG)
      }
      else
        originalSender ! GetStartByRestaurantResponse(0)

      unstashAll()
      context.become(state(readerStarsByRestaurantState))

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


  override def receiveCommand: Receive = state(ReaderStarsByRestaurantState(Set()))

  override def receiveRecover: Receive = {
    case ReviewCreated(id, _, _) =>
      log.info(s"ReaderStarsByRestaurant has recovered a review with id: $id")
      readerStarsByRestaurantRecoveryState = readerStarsByRestaurantRecoveryState.copy(
                                                            reviews = readerStarsByRestaurantRecoveryState.reviews + id)

      context.become(state(readerStarsByRestaurantRecoveryState))

    case ReviewUpdated(id, _, _) => log.info(s"ReaderFilterByCategories has recovered a update " +
      s"for review with id: $id")
  }

  // Auxiliary methods
  def getAllStarsByRestaurant(restaurantId: String): Future[GetAllStarsByRestaurantResponse] = {
    val eventsWithSequenceSource = readerDatabaseUtility.getSourceEventSByTag(restaurantId)
    val ranGraph: Future[Seq[Int]] = runGraphQueryReader(eventsWithSequenceSource, readerDatabaseUtility.materializer,
                                                         restaurantId)

    ranGraph.map(GetAllStarsByRestaurantResponse)
  }
}

package com.vgomez.app.actors.readers

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import akka.persistence.PersistentActor
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.EventEnvelope
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.vgomez.app.actors.Review.Command.GetReview
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.abtractions.Abstract.Event.Event
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.Response.GetEventsIdsResponse

import scala.concurrent.Future

object ReaderStarsByRestaurant {
  // state
  case class ReaderStarsByRestaurantState(reviews: Set[String])

  // commands
  object Command {
    case class CreateReview(id: String, restaurantId: String)
    case class UpdateReview(id: String, restaurantId: String)
    case class GetStarsByRestaurant(restaurantId: String)
  }

  object Response {
    case class GetStarsByRestaurantResponse(stars: Int)
  }

  // events
  case class ReviewCreated(id: String, restaurantId: String) extends Event
  case class ReviewUpdated(id: String, restaurantId: String) extends Event

  def props(system: ActorSystem): Props =  Props(new ReaderStarsByRestaurant(system))

  // WriteEventAdapter
  class ReaderStarByRestaurantAdapter extends WriteEventAdapter {
    override def toJournal(event: Any): Any = event match {
      case ReviewCreated(_, restaurantId) =>
        Tagged(event, Set(restaurantId))
      case ReviewUpdated(_, restaurantId) =>
        Tagged(event, Set(restaurantId))
      case _ =>
        event
    }
    override def manifest(event: Any): String = ""
  }

  def runGraphQueryReader(eventsWithSequenceSource: Source[EventEnvelope, NotUsed], materializer: Materializer,
                          queryRestaurantId: String): Future[Seq[String]] = {
    val eventsSource = eventsWithSequenceSource.map(_.event)

    val flowFilter = Flow[Any].filter {
      case ReviewCreated(_, restaurantId) if restaurantId == queryRestaurantId => true
      case ReviewUpdated(_, restaurantId) if restaurantId == queryRestaurantId => true
      case _ => false
    }

    val flowMap = Flow[Any].map {
      case ReviewCreated(id, _) => id
      case ReviewUpdated(id, _) => id
      case _ => ""
    }

    val sink = Sink.seq[String]
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
    case CreateReview(id, restaurantId) =>
      val newState: ReaderStarsByRestaurantState = readerStarsByRestaurantState.copy(
                                                          reviews = readerStarsByRestaurantState.reviews + id)
      persist(ReviewCreated(id, restaurantId)) { _ =>
        log.info(s"ReaderStarsByRestaurant create a review with id: $id")
        context.become(state(newState))
      }

    case UpdateReview(id, categories) =>
      persist(ReviewUpdated(id, categories)) { _ =>
        log.info(s"ReaderStarsByRestaurant update a review with id: $id")
      }

    case GetStarsByRestaurant(restaurantId) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByFavoriteCategories command.")
      getReviewsIdsByRestaurant(restaurantId).mapTo[GetEventsIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getStartByRestaurantState(readerStarsByRestaurantState, sender(), restaurantId, Int.MaxValue))

    case _ =>
      stash()
  }
  
  def getStartByRestaurantState(readerStarsByRestaurantState: ReaderStarsByRestaurantState,
                                originalSender: ActorRef, queryRestaurantId: String,
                                totalAmountId: Int, currentAmountId: Int = 0,
                                accResponses: List[GetReviewResponse] = List()): Receive = {

    case GetEventsIdsResponse(idsReviews) =>
      processGetEventsIdsResponseCommand(readerStarsByRestaurantState, originalSender,queryRestaurantId, idsReviews)

    case getResponse@GetReviewResponse(Some(reviewState)) =>
      processGetResponseCommand(readerStarsByRestaurantState, originalSender, queryRestaurantId, totalAmountId,
        currentAmountId, accResponses, getResponse, Some(reviewState.restaurantId), none = false)

    case getResponse@GetReviewResponse(None) =>
      processGetResponseCommand(readerStarsByRestaurantState, originalSender, queryRestaurantId, totalAmountId,
        currentAmountId, accResponses, getResponse, None, none = false)

    case _ =>
      stash()
  }


  override def receiveCommand: Receive = state(ReaderStarsByRestaurantState(Set()))

  override def receiveRecover: Receive = {
    case ReviewCreated(id, _) =>
      log.info(s"ReaderStarsByRestaurant has recovered a review with id: $id")
      readerStarsByRestaurantRecoveryState = readerStarsByRestaurantRecoveryState.copy(
                                                            reviews = readerStarsByRestaurantRecoveryState.reviews + id)

      context.become(state(readerStarsByRestaurantRecoveryState))

    case ReviewUpdated(id, _) => log.info(s"ReaderFilterByCategories has recovered a update " +
      s"for review with id: $id")
  }

  // Auxiliary methods ReaderDatabaseUtility
  def getReviewsIdsByRestaurant(restaurantId: String): Future[GetEventsIdsResponse] = {
    val eventsWithSequenceSource = readerDatabaseUtility.getSourceEventSByTag(restaurantId)
    val ranGraph: Future[Seq[String]] = runGraphQueryReader(eventsWithSequenceSource, readerDatabaseUtility.materializer,
                                                         restaurantId)
    ranGraph.map(ids => GetEventsIdsResponse(ids.toSet))
  }

  // Auxiliary methods getStartByRestaurantState.
  def processGetEventsIdsResponseCommand(readerStarsByRestaurantState: ReaderStarsByRestaurantState,
                                         originalSender: ActorRef, queryRestaurantId: String, ids: Set[String]): Unit = {
    val currentAmountId: Int = 0
    val accResponses = List()

    if (ids.nonEmpty) {
      log.info("getting ids")
      ids.foreach(id => context.parent ! GetReview(id))
      context.become(getStartByRestaurantState(readerStarsByRestaurantState, originalSender,queryRestaurantId,
                                                ids.size, currentAmountId, accResponses))
    }
    else {
      originalSender ! GetStarsByRestaurantResponse(0)
      unstashAll()
      context.become(state(readerStarsByRestaurantState))
    }
  }

  def processGetResponseCommand(readerStarsByRestaurantState: ReaderStarsByRestaurantState, originalSender: ActorRef,
                                queryRestaurantId: String, totalAmountId: Int, currentAmountId: Int,
                                accResponses: List[GetReviewResponse], getResponse: GetReviewResponse,
                                reviewRestaurantId: Option[String], none: Boolean): Unit = {

    log.info("receiving GetResponse from administration.")

    if (currentAmountId + 1 >= totalAmountId) {
      log.info(s"finishing currentAmountId: ${currentAmountId + 1} of total: $totalAmountId.")
      if (!none && queryRestaurantId == reviewRestaurantId.getOrElse("")) {
        val starsList: List[Int] = (accResponses :+ getResponse).map(getReviewResponse =>
                                                                          getReviewResponse.maybeReviewState.get.stars)
        val startAVG: Int = starsList.sum / starsList.length
        originalSender ! GetStarsByRestaurantResponse(startAVG)
      } else {
        val starsList: List[Int] = (accResponses).map(getReviewResponse =>
          getReviewResponse.maybeReviewState.get.stars)

        val startAVG: Int = starsList.sum / starsList.length
        originalSender ! GetStarsByRestaurantResponse(startAVG)
      }

      unstashAll()
      context.become(state(readerStarsByRestaurantState))
    }
    else {
      log.info(s"becoming currentAmountId: $currentAmountId of total: $totalAmountId.")
      if (!none && queryRestaurantId == reviewRestaurantId.getOrElse(""))
        context.become(getStartByRestaurantState(readerStarsByRestaurantState, originalSender, queryRestaurantId,
          totalAmountId, currentAmountId + 1, accResponses :+ getResponse))
      else
        context.become(getStartByRestaurantState(readerStarsByRestaurantState, originalSender, queryRestaurantId,
          totalAmountId, currentAmountId + 1, accResponses))
    }
  }

}

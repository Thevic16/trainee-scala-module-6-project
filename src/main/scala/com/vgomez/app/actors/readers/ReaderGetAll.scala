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
import com.vgomez.app.actors.Review.Command.GetReview
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Event.Event
import com.vgomez.app.actors.abtractions.Abstract.Response.GetResponse
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.Response._
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.getMaterializeGraphReaderUtility
import com.vgomez.app.actors.readers.ReaderUtility._

import scala.concurrent.Future

object ReaderGetAll {
  // state
  case class ReaderGetAllState(restaurants: Set[String], reviews: Set[String], users: Set[String])

  // Actor types
  object ActorType {
    val restaurantType = "restaurantType"
    val reviewType = "reviewType"
    val userType = "userType"
  }

  // commands
  object Command {
    case class CreateRestaurant(id: String)
    case class CreateReview(id: String)
    case class CreateUser(username: String)
    case class GetAllRestaurant(pageNumber: Long, numberOfElementPerPage: Long)
    case class GetAllReview(pageNumber: Long, numberOfElementPerPage: Long)
    case class GetAllUser(pageNumber: Long, numberOfElementPerPage: Long)
  }

  object Response {
    case class GetAllRestaurantResponse(optionGetRestaurantResponses: Option[List[GetRestaurantResponse]])
    case class GetAllReviewResponse(optionGetReviewResponses: Option[List[GetReviewResponse]])
    case class GetAllUserResponse(optionGetUserResponses: Option[List[GetUserResponse]])
  }

  // events
  case class RestaurantCreated(id: String) extends Event
  case class ReviewCreated(id: String) extends Event
  case class UserCreated(username: String) extends Event

  def props(system: ActorSystem): Props =  Props(new ReaderGetAll(system))

  // WriteEventAdapter
  class ReaderGetAllEventAdapter extends WriteEventAdapter {
    import ActorType._
    override def toJournal(event: Any): Any = event match {
      case RestaurantCreated(_) =>
        Tagged(event, Set(restaurantType))
      case ReviewCreated(_) =>
        Tagged(event, Set(reviewType))
      case UserCreated(_) =>
        Tagged(event, Set(userType))
      case _ =>
        event
    }
    override def manifest(event: Any): String = ""
  }

  def getGraphQueryReader(eventsWithSequenceSource: Source[EventEnvelope,
    NotUsed]): RunnableGraph[Future[Seq[String]]] = {
    val flowMapGetIdFromEvent = Flow[Any].map {
      case RestaurantCreated(id) => id
      case ReviewCreated(id) => id
      case UserCreated(id) => id
      case _ => ""
    }
    getMaterializeGraphReaderUtility(eventsWithSequenceSource, flowMapGetIdFromEvent)
  }
}

class ReaderGetAll(system: ActorSystem) extends PersistentActor with ActorLogging with Stash {

  import ReaderGetAll._
  import Command._
  import Response._
  import ActorType._
  import system.dispatcher

  // ReaderDatabaseUtility
  val readerDatabaseUtility = ReaderDatabaseUtility(system)

  // state
  var readerGetAllRecoveryState = ReaderGetAllState(Set(), Set(), Set())

  override def persistenceId: String = "reader-get-all"

  def state(readerGetAllState: ReaderGetAllState): Receive = {
    case CreateRestaurant(id) =>
      val newState: ReaderGetAllState = readerGetAllState.copy(restaurants = readerGetAllState.restaurants + id)
      persist(RestaurantCreated(id)) { _ =>
        log.info(s"ReaderGetAll create a restaurant with id: $id")
        context.become(state(newState))
      }

    case CreateReview(id) =>
      val newState: ReaderGetAllState = readerGetAllState.copy(reviews =
        readerGetAllState.reviews + id)

      persist(ReviewCreated(id)) { _ =>
        log.info(s"ReaderGetAll create a review with id: $id")
        context.become(state(newState))
      }

    case CreateUser(username) =>
      val newState: ReaderGetAllState = readerGetAllState.copy(users =
        readerGetAllState.users + username)

      persist(UserCreated(username)) { _ =>
        log.info(s"ReaderGetAll create a user with id: $username")
        context.become(state(newState))
      }

    case GetAllRestaurant(pageNumber, numberOfElementPerPage) =>
      log.info("ReaderGetAll has receive a GetAllRestaurant command.")
      getEventsIdsByActorType(restaurantType, pageNumber, numberOfElementPerPage).mapTo[GetEventsIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurantState(readerGetAllState, sender(), Int.MaxValue))

    case GetAllReview(pageNumber, numberOfElementPerPage) =>
      log.info("ReaderGetAll has receive a GetAllReview command.")
      getEventsIdsByActorType(reviewType, pageNumber, numberOfElementPerPage).mapTo[GetEventsIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllReviewState(readerGetAllState, sender(), Int.MaxValue))

    case GetAllUser(pageNumber, numberOfElementPerPage) =>
      log.info("ReaderGetAll has receive a GetAllUser command.")
      getEventsIdsByActorType(userType, pageNumber, numberOfElementPerPage).mapTo[GetEventsIdsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllUserState(readerGetAllState, sender(), Int.MaxValue))

    case _ =>
      stash()
  }
  
  def getAllRestaurantState(readerGetAllState: ReaderGetAllState, originalSender: ActorRef, totalAmountId: Int,
                       currentAmountId: Int = 0,
                       accResponses: List[GetRestaurantResponse] = List()): Receive = {

    case GetEventsIdsResponse(ids) =>
      processGetEventsIdsResponseCommand(readerGetAllState, originalSender, ActorType.restaurantType, ids)

    case getResponse@GetRestaurantResponse(Some(_), Some(_)) =>
      processGetResponseCommand(readerGetAllState, originalSender, totalAmountId, currentAmountId,
                                        accResponses, getResponse, none = false)

    case getResponse@GetRestaurantResponse(None, None) =>
      processGetResponseCommand(readerGetAllState, originalSender, totalAmountId, currentAmountId,
        accResponses, getResponse, none = true)

    case _ =>
      stash()
  }

  def getAllReviewState(readerGetAllState: ReaderGetAllState, originalSender: ActorRef, totalAmountId: Int,
                   currentAmountId: Int = 0, accResponses: List[GetReviewResponse] = List()): Receive = {

    case GetEventsIdsResponse(ids) =>
      processGetEventsIdsResponseCommand(readerGetAllState, originalSender, ActorType.reviewType, ids)

    case getResponse@GetReviewResponse(Some(_)) =>
      processGetResponseCommand(readerGetAllState, originalSender, totalAmountId, currentAmountId,
        accResponses, getResponse, none = false)

    case getResponse@GetReviewResponse(None) =>
      processGetResponseCommand(readerGetAllState, originalSender, totalAmountId, currentAmountId,
        accResponses, getResponse, none = true)

    case _ =>
      stash()
  }


  def getAllUserState(readerGetAllState: ReaderGetAllState, originalSender: ActorRef, totalAmountId: Int,
                   currentAmountId: Int = 0, accResponses: List[GetUserResponse] = List()): Receive = {

    case GetEventsIdsResponse(ids) =>
      processGetEventsIdsResponseCommand(readerGetAllState, originalSender, ActorType.userType, ids)

    case getResponse@GetUserResponse(Some(_)) =>
      processGetResponseCommand(readerGetAllState, originalSender, totalAmountId, currentAmountId,
        accResponses, getResponse, none = false)

    case getResponse@GetUserResponse(None) =>
      processGetResponseCommand(readerGetAllState, originalSender, totalAmountId, currentAmountId,
        accResponses, getResponse, none = true)

    case _ =>
      stash()
  }


  override def receiveCommand: Receive = state(ReaderGetAllState(Set(), Set(), Set()))

  override def receiveRecover: Receive = {
    case RestaurantCreated(id) =>
      log.info(s"ReaderGetAll has recovered a restaurant with id: $id")
      readerGetAllRecoveryState = readerGetAllRecoveryState.copy(
        restaurants = readerGetAllRecoveryState.restaurants + id)

      context.become(state(readerGetAllRecoveryState))

    case ReviewCreated(id) =>
      log.info(s"ReaderGetAll has recovered a review with id: $id")
      readerGetAllRecoveryState = readerGetAllRecoveryState.copy(
        reviews = readerGetAllRecoveryState.reviews + id)

      context.become(state(readerGetAllRecoveryState))

    case UserCreated(username) =>
      log.info(s"ReaderGetAll has recovered a user with username: $username")
      readerGetAllRecoveryState = readerGetAllRecoveryState.copy(
        users = readerGetAllRecoveryState.users + username)

      context.become(state(readerGetAllRecoveryState))
  }

  // Auxiliary methods reader database
  def getEventsIdsByActorType(actorType: String, pageNumber: Long,
                           numberOfElementPerPage: Long): Future[GetEventsIdsResponse] = {
    val eventsWithSequenceSource = readerDatabaseUtility.getSourceEventSByTagWithPagination(actorType, pageNumber,
                                                                                            numberOfElementPerPage)
    val graph: RunnableGraph[Future[Seq[String]]] = getGraphQueryReader(eventsWithSequenceSource)
    readerDatabaseUtility.runGraph(graph)
  }

  // Auxiliary methods getAll States
  def processGetEventsIdsResponseCommand(readerGetAllState: ReaderGetAllState, originalSender: ActorRef,
                                         actorType: String, ids: Set[String]): Unit = {
    val currentAmountId: Int = 0
    val accResponses = List()

    if(ids.nonEmpty){
      log.info("getting ids")
      actorType match {
        case ActorType.restaurantType =>
          ids.foreach(id => context.parent ! GetRestaurant(id))
          context.become(getAllRestaurantState(readerGetAllState, originalSender, ids.size, currentAmountId, accResponses))
        case ActorType.reviewType =>
          ids.foreach(id => context.parent ! GetReview(id))
          context.become(getAllReviewState(readerGetAllState, originalSender, ids.size, currentAmountId, accResponses))
        case ActorType.userType =>
          ids.foreach(id => context.parent ! GetUser(id))
          context.become(getAllUserState(readerGetAllState, originalSender, ids.size, currentAmountId, accResponses))
      }
    }
    else {
      actorType match {
        case ActorType.restaurantType =>
          originalSender ! GetAllRestaurantResponse(None)
        case ActorType.reviewType =>
          originalSender ! GetAllReviewResponse(None)
        case ActorType.userType =>
          originalSender ! GetAllUserResponse(None)
      }
      unstashAll()
      context.become(state(readerGetAllState))
    }

  }

  def processGetResponseCommand(readerGetAllState: ReaderGetAllState, originalSender: ActorRef,
                                        totalAmountId: Int, currentAmountId: Int, accResponses: List[GetResponse],
                                        getResponse: GetResponse, none: Boolean): Unit = {
    log.info("receiving GetResponse from administration.")


    if(currentAmountId+1 >= totalAmountId) {
      log.info(s"finishing currentAmountId: ${currentAmountId+1} of total: $totalAmountId.")
      getResponse match {
        case getResponse@GetRestaurantResponse(_, _) =>
          if(none)
            originalSender ! GetAllRestaurantResponse(Some(getRestaurantResponseList(accResponses)))
          else
            originalSender ! GetAllRestaurantResponse(Some(getRestaurantResponseList(accResponses) :+ getResponse))

        case getResponse@GetReviewResponse(_) =>
          if(none)
            originalSender ! GetAllReviewResponse(Some(getReviewResponseList(accResponses)))
          else
            originalSender ! GetAllReviewResponse(Some(getReviewResponseList(accResponses) :+ getResponse))

        case getResponse@GetUserResponse(_) =>
          if(none)
            originalSender ! GetAllUserResponse(Some(getUserResponseList(accResponses)))
          else
            originalSender ! GetAllUserResponse(Some(getUserResponseList(accResponses) :+ getResponse))
      }
      unstashAll()
      context.become(state(readerGetAllState))
    }
    else {
      log.info(s"becoming currentAmountId: $currentAmountId of total: $totalAmountId.")
      getResponse match {
        case getResponse@GetRestaurantResponse(_, _) =>
          if(none)
            context.become(getAllRestaurantState(readerGetAllState, originalSender, totalAmountId, currentAmountId + 1,
              getRestaurantResponseList(accResponses)))
          else
            context.become(getAllRestaurantState(readerGetAllState, originalSender, totalAmountId, currentAmountId + 1,
              getRestaurantResponseList(accResponses) :+ getResponse))

        case getResponse@GetReviewResponse(_) =>
          if(none)
            context.become(getAllReviewState(readerGetAllState, originalSender, totalAmountId, currentAmountId + 1,
              getReviewResponseList(accResponses)))
          else
            context.become(getAllReviewState(readerGetAllState, originalSender, totalAmountId, currentAmountId + 1,
              getReviewResponseList(accResponses) :+ getResponse))

        case getResponse@GetUserResponse(_) =>
          if(none)
            context.become(getAllUserState(readerGetAllState, originalSender, totalAmountId, currentAmountId + 1,
              getUserResponseList(accResponses)))
          else
            context.become(getAllUserState(readerGetAllState, originalSender, totalAmountId, currentAmountId + 1,
              getUserResponseList(accResponses) :+ getResponse))
      }
    }
  }

}

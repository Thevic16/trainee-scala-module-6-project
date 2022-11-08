
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.actors

import akka.Done
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.vgomez.app.actors.AdministrationUtility._
import com.vgomez.app.actors.intermediate.IntermediateReadUserAttributes
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Event._
import com.vgomez.app.actors.readers.{ReaderFilterByCategories, ReaderFilterByLocation, ReaderGetAll,
  ReaderStarsByRestaurant}
import com.vgomez.app.actors.writers.WriterProjection
import com.vgomez.app.domain.DomainModel.Location
import com.vgomez.app.exception.CustomException._

import scala.concurrent.duration._
import scala.util.{Failure, Success}


object Administration {
  // state
  case class AdministrationState(restaurants: Map[String, (Long, ActorRef)], reviews: Map[String,
    (Long, ActorRef)], users: Map[String, (Long, ActorRef)], currentRestaurantIndex: Long,
                                 currentReviewIndex: Long, currentUserIndex: Long)

  // commands
  object Command {
    case class GetStarsByRestaurant(restaurantId: String)

    case class GetAllRestaurant(pageNumber: Long, numberOfElementPerPage: Long)

    case class GetAllReview(pageNumber: Long, numberOfElementPerPage: Long)

    case class GetAllUser(pageNumber: Long, numberOfElementPerPage: Long)

    // Recommendations Categories
    case class GetRecommendationFilterByFavoriteCategories(favoriteCategories: Set[String], pageNumber: Long,
                                                           numberOfElementPerPage: Long)

    case class GetRecommendationFilterByUserFavoriteCategories(username: String, pageNumber: Long,
                                                               numberOfElementPerPage: Long)

    // Recommendations Location
    case class GetRecommendationCloseToLocation(location: Location, rangeInKm: Double, pageNumber: Long,
                                                numberOfElementPerPage: Long)

    case class GetRecommendationCloseToMe(username: String, rangeInKm: Double, pageNumber: Long,
                                          numberOfElementPerPage: Long)
  }

  // events
  case class RestaurantRegistered(id: String) extends EventAdministration

  case class ReviewRegistered(id: String) extends EventAdministration

  case class UserRegistered(username: String) extends EventAdministration

  def props(system: ActorSystem): Props = Props(new Administration(system))
}

class Administration(system: ActorSystem) extends PersistentActor with ActorLogging {

  import Administration._

  // Commands
  import Command._
  import Restaurant.Command._
  import Review.Command._
  import User.Command._
  import context.dispatcher

  // intermediates
  val intermediateReadUserAttributes: ActorRef = context.actorOf(Props[IntermediateReadUserAttributes],
    "intermediate-read-user-attributes")

  // Readers
  val readerGetAll: ActorRef = context.actorOf(ReaderGetAll.props(system), "reader-get-all")
  val readerFilterByCategories: ActorRef = context.actorOf(ReaderFilterByCategories.props(system,
    intermediateReadUserAttributes), "reader-filter-by-categories")
  val readerFilterByLocation: ActorRef = context.actorOf(ReaderFilterByLocation.props(system,
    intermediateReadUserAttributes), "reader-filter-by-location")
  val readerStarsByRestaurant: ActorRef = context.actorOf(ReaderStarsByRestaurant.props(system),
    "reader-stars-by-restaurant")

  // Writers
  val writerProjection: ActorRef = context.actorOf(WriterProjection.props(system), "writer-projection")

  val writerProjectionScheduler: Cancellable = context.system.scheduler.scheduleWithFixedDelay(Duration.Zero,
    delay = 5 seconds, writerProjection, WriterProjection.Command.StartProjection)

  // utility
  var administrationRecoveryState: AdministrationState = AdministrationState(restaurants = Map(),
    reviews = Map(), users = Map(), currentRestaurantIndex = 0, currentReviewIndex = 0, currentUserIndex = 0)

  override def persistenceId: String = "administration"

  override def receiveCommand: Receive = state(administrationRecoveryState)

  override def receiveRecover: Receive = recoverState

  // States
  def state(administrationState: AdministrationState): Receive = {
    restaurantState(administrationState).orElse(reviewState(administrationState)).
      orElse(userState(administrationState)).orElse(recommendationState(administrationState)).
      orElse(confirmationState)
  }

  def restaurantState(administrationState: AdministrationState): Receive = {
    // Restaurants Commands
    case getCommand@GetRestaurant(_) =>
      processGetCommand(getCommand, administrationState)

    case GetStarsByRestaurant(restaurantId) =>
      log.info("Administration receive a GetStartByRestaurant Command.")
      readerStarsByRestaurant.forward(ReaderStarsByRestaurant.Command.GetStarsByRestaurant(restaurantId))

    case GetAllRestaurant(pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetAllRestaurant command.")
      readerGetAll.forward(ReaderGetAll.Command.GetAllRestaurant(pageNumber, numberOfElementPerPage))

    case registerCommand@RegisterRestaurant(_, _) =>
      processRegisterCommandWithVerifyIds(registerCommand, administrationState)

    case updateCommand@UpdateRestaurant(_, _) =>
      processUpdateCommandWithVerifyIds(updateCommand, administrationState)

    case unregisterCommand@UnregisterRestaurant(_) =>
      processUnregisterCommand(unregisterCommand, administrationState)
  }

  def reviewState(administrationState: AdministrationState): Receive = {
    // Reviews Commands
    case getCommand@GetReview(_) =>
      processGetCommand(getCommand, administrationState)

    case GetAllReview(pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetAllReview command.")
      readerGetAll.forward(ReaderGetAll.Command.GetAllReview(pageNumber, numberOfElementPerPage))

    case registerCommand@RegisterReview(_, _) =>
      processRegisterCommandWithVerifyIds(registerCommand, administrationState)

    case updateCommand@UpdateReview(_, _) =>
      processUpdateCommandWithVerifyIds(updateCommand, administrationState)

    case unregisterCommand@UnregisterReview(_) =>
      log.info("Administration has receive a UnregisterReview command.")
      processUnregisterCommand(unregisterCommand, administrationState)
  }

  def userState(administrationState: AdministrationState): Receive = {
    // Users Commands
    case getCommand@GetUser(_) =>
      processGetCommand(getCommand, administrationState)

    case GetAllUser(pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetAllUser command.")
      readerGetAll.forward(ReaderGetAll.Command.GetAllUser(pageNumber, numberOfElementPerPage))

    case registerCommand@RegisterUser(_) =>
      processRegisterCommand(registerCommand, administrationState)

    case updateCommand@UpdateUser(_) =>
      processUpdateCommand(updateCommand, administrationState)

    case unregisterCommand@UnregisterUser(_) =>
      processUnregisterCommand(unregisterCommand, administrationState)
  }

  def confirmationState: Receive = {
    // Confirmation projection process has stated successfully
    case Done =>
      writerProjectionScheduler.cancel() // Cancelling the scheduler
      log.info("Projection process has stated successfully")
  }

  def recommendationState(administrationState: AdministrationState): Receive = {
    // Recommendations By Categories Commands
    case GetRecommendationFilterByFavoriteCategories(favoriteCategories, pageNumber,
    numberOfElementPerPage) => log.info("Administration has receive a " +
      "GetRecommendationFilterByFavoriteCategories command.")
      readerFilterByCategories.forward(
        ReaderFilterByCategories.Command.GetRecommendationFilterByFavoriteCategories(favoriteCategories,
          pageNumber, numberOfElementPerPage))

    case GetRecommendationFilterByUserFavoriteCategories(username, pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetRecommendationFilterByFavoriteCategories command.")
      readerFilterByCategories.forward(
        ReaderFilterByCategories.Command.GetRecommendationFilterByUserFavoriteCategories(username, pageNumber,
          numberOfElementPerPage))

    // Recommendations By Locations Commands
    case GetRecommendationCloseToLocation(location, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetRecommendationCloseToLocation command.")
      readerFilterByLocation.forward(
        ReaderFilterByLocation.Command.GetRecommendationCloseToLocation(location, rangeInKm, pageNumber,
          numberOfElementPerPage))

    case GetRecommendationCloseToMe(username, rangeInKm, pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetRecommendationCloseToMe command.")
      readerFilterByLocation.forward(ReaderFilterByLocation.Command.GetRecommendationCloseToMe(username,
        rangeInKm, pageNumber, numberOfElementPerPage))
  }

  def recoverState: Receive = {
    case RestaurantRegistered(id) =>
      log.info(s"Administration has recovered a restaurant with id: $id")
      val restaurant = context.child(id).getOrElse(context.actorOf(Restaurant.props(id,
        administrationRecoveryState.currentRestaurantIndex), id))

      administrationRecoveryState = administrationRecoveryState.copy(
        restaurants = administrationRecoveryState.restaurants +
          (id -> (administrationRecoveryState.currentRestaurantIndex, restaurant)),
        currentRestaurantIndex = administrationRecoveryState.currentRestaurantIndex + 1)

    case ReviewRegistered(id) =>
      log.info(s"Administration has recovered a review with id: $id")
      val review = context.child(id).getOrElse(context.actorOf(Review.props(id,
        administrationRecoveryState.currentReviewIndex), id))

      administrationRecoveryState = administrationRecoveryState.copy(
        reviews = administrationRecoveryState.reviews + (id ->
          (administrationRecoveryState.currentReviewIndex, review)),
        currentReviewIndex = administrationRecoveryState.currentReviewIndex + 1)


    case UserRegistered(username) =>
      log.info(s"Administration has recovered a user with username: $username")
      val user = context.child(username).getOrElse(context.actorOf(User.props(username,
        administrationRecoveryState.currentUserIndex), username))

      administrationRecoveryState = administrationRecoveryState.copy(
        users = administrationRecoveryState.users +
          (username -> (administrationRecoveryState.currentUserIndex, user)),
        currentUserIndex = administrationRecoveryState.currentUserIndex + 1)

    case RecoveryCompleted =>
      context.become(state(administrationRecoveryState))
  }


  // Methods to reduce code on API process
  def processGetCommand(getCommand: GetCommand, administrationState: AdministrationState): Unit = {
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByGetCommand(getCommand,
      administrationState)

    actorRefOption match {
      case Some((_, actorRef)) =>
        actorRef.forward(getCommand)
      case None =>
        sender() ! None
    }
  }

  def processRegisterCommand(registerCommand: RegisterCommand,
                             administrationState: AdministrationState): Unit = {
    val identifier: String = getIdentifierByRegisterCommand(registerCommand)
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByRegisterCommand(registerCommand,
      identifier, administrationState)
    actorRefOption match {
      case Some(_) =>
        sender() ! getRegisterResponseExistsExceptionByRegisterCommand(registerCommand)

      case None =>
        val newActorRef: ActorRef = getNewActorRefByRegisterCommand(context, administrationState,
          registerCommand, identifier)
        val newStateAdministrationState: AdministrationState = getNewStateByRegisterCommand(registerCommand,
          newActorRef, identifier, administrationState)
        persistRegisterCommand(registerCommand, newActorRef, identifier, newStateAdministrationState)
    }
  }

  def processRegisterCommandWithVerifyIds(registerCommand: RegisterCommand,
                                          administrationState: AdministrationState): Unit = {
    verifyIdsOnRegisterCommand(registerCommand, administrationState) match {
      case Success(_) =>
        processRegisterCommand(registerCommand, administrationState)
      case Failure(exception) =>
        sender() ! Failure(exception)
    }
  }

  def persistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef, identifier: String,
                             newStateAdministrationState: AdministrationState): Unit = {
    registerCommand match {
      case RegisterRestaurant(_, _) =>
        helperPersistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef,
          identifier: String, newStateAdministrationState: AdministrationState, actorName = "restaurant",
          RestaurantRegistered(identifier))

      case RegisterReview(_, _) =>
        helperPersistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef,
          identifier: String, newStateAdministrationState: AdministrationState, actorName = "review",
          ReviewRegistered(identifier))

      case RegisterUser(_) =>
        helperPersistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef,
          identifier: String, newStateAdministrationState: AdministrationState, actorName = "user",
          UserRegistered(identifier))
    }
  }

  def helperPersistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef,
                                   identifier: String, newStateAdministrationState: AdministrationState,
                                   actorName: String, event: EventAdministration): Unit = {
    persist(event) { _ =>
      log.info(s"Administration has Registered a $actorName with id: $identifier")
      newActorRef.forward(registerCommand)
      context.become(state(newStateAdministrationState))
    }
  }

  def processUpdateCommand(updateCommand: UpdateCommand, administrationState: AdministrationState): Unit = {
    val identifier: String = getIdentifierByUpdateCommand(updateCommand)
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByUpdateCommand(updateCommand, identifier,
      administrationState)
    actorRefOption match {
      case Some((_, actorRef)) =>
        actorRef.forward(updateCommand)

      case None =>
        sender() ! getUpdateResponseNotFoundExceptionByUpdateCommand(updateCommand)
    }
  }

  def processUpdateCommandWithVerifyIds(updateCommand: UpdateCommand,
                                        administrationState: AdministrationState): Unit = {
    verifyIdsOnUpdateCommand(updateCommand, administrationState) match {
      case Success(_) =>
        processUpdateCommand(updateCommand, administrationState)
      case Failure(RestaurantNotFoundException(message)) =>
        sender() ! getUpdateResponseNotFoundExceptionByUpdateCommandWithMessage(updateCommand, message)
      case Failure(ReviewNotFoundException(message)) =>
        sender() ! getUpdateResponseNotFoundExceptionByUpdateCommandWithMessage(updateCommand, message)
      case Failure(UserNotFoundException(message)) =>
        sender() ! getUpdateResponseNotFoundExceptionByUpdateCommandWithMessage(updateCommand, message)
    }
  }

  def processUnregisterCommand(unregisterCommand: UnregisterCommand,
                               administrationState: AdministrationState): Unit = {
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByUnregisterCommand(unregisterCommand,
      administrationState)
    actorRefOption match {
      case Some((_, actorRef)) =>
        actorRef.forward(unregisterCommand)

      case None =>
        sender() ! getUnregisterResponseNotFoundExceptionByUnregisterCommand(unregisterCommand)
    }
  }
}

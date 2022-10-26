package com.vgomez.app.actors
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.vgomez.app.exception.CustomException._

import scala.util.{Failure, Success}
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Response._
import com.vgomez.app.actors.messages.AbstractMessage.Event._
import com.vgomez.app.actors.AdministrationUtility._
import com.vgomez.app.actors.readers.{ReaderFilterByCategories, ReaderFilterByLocation, ReaderGetAll,
                                      ReaderStarsByRestaurant}
import com.vgomez.app.actors.writers.WriterToIndexDatabase
import com.vgomez.app.domain.DomainModel.Location

/*
Todo #17
  Description: Every message passes throughout Administration Actor, finds a way to enhance this. (Bottleneck)
  Status: No started
  Reported by: Nafer Sanabria.
*/
object Administration {
  // state
  case class AdministrationState(restaurants: Map[String, (Long, ActorRef)], reviews: Map[String, (Long, ActorRef)],
                                 users: Map[String, (Long, ActorRef)], currentRestaurantIndex: Long,
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
  case class RestaurantRegistered(id: String) extends Event
  case class ReviewRegistered(id: String) extends Event
  case class UserRegistered(username: String) extends Event
  def props(system: ActorSystem): Props =  Props(new Administration(system))
}

class Administration(system: ActorSystem) extends PersistentActor with ActorLogging{
  import Administration._

  // Commands
  import Restaurant.Command._
  import Review.Command._
  import User.Command._
  import Command._

  // Readers
  val readerGetAll = context.actorOf(ReaderGetAll.props(system), "reader-get-all")
  val readerFilterByCategories = context.actorOf(ReaderFilterByCategories.props(system),
                                            "reader-filter-by-categories")
  val readerFilterByLocation = context.actorOf(ReaderFilterByLocation.props(system),
                                          "reader-filter-by-location")
  val readerStarsByRestaurant = context.actorOf(ReaderStarsByRestaurant.props(system),
                                        "reader-stars-by-restaurant")

  val writerToIndexDatabase = context.actorOf(WriterToIndexDatabase.props(system), "writer-to-index-database")

  // for state recovery
  var administrationRecoveryState = AdministrationState(Map(), Map(), Map(), 0, 0, 0)

  override def persistenceId: String = "administration"

  def state(administrationState: AdministrationState): Receive = {
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


    // Recommendations By Categories Commands
    case GetRecommendationFilterByFavoriteCategories(favoriteCategories, pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetRecommendationFilterByFavoriteCategories command.")
      readerFilterByCategories.forward(
        ReaderFilterByCategories.Command.GetRecommendationFilterByFavoriteCategories(favoriteCategories, pageNumber,
                                                                                      numberOfElementPerPage))

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
      readerFilterByLocation.forward(ReaderFilterByLocation.Command.GetRecommendationCloseToMe(username, rangeInKm,
                                                                                              pageNumber,
                                                                                              numberOfElementPerPage))
  }

  override def receiveCommand: Receive = state(administrationRecoveryState)

  override def receiveRecover: Receive = {
    case RestaurantRegistered(id) =>
        log.info(s"Administration has recovered a restaurant with id: $id")
        val restaurant = context.child(id).getOrElse(context.actorOf(Restaurant.props(id,
                                                               administrationRecoveryState.currentRestaurantIndex), id))

        administrationRecoveryState = administrationRecoveryState.copy(
          restaurants = administrationRecoveryState.restaurants +
                                               (id -> (administrationRecoveryState.currentRestaurantIndex, restaurant)),
          currentRestaurantIndex = administrationRecoveryState.currentRestaurantIndex + 1)

        context.become(state(administrationRecoveryState))

    case ReviewRegistered(id) =>
      log.info(s"Administration has recovered a review with id: $id")
      val review = context.child(id).getOrElse(context.actorOf(Review.props(id,
                                                                   administrationRecoveryState.currentReviewIndex), id))

      administrationRecoveryState = administrationRecoveryState.copy(
        reviews = administrationRecoveryState.reviews + (id -> (administrationRecoveryState.currentReviewIndex, review)),
        currentReviewIndex = administrationRecoveryState.currentReviewIndex + 1)

      context.become(state(administrationRecoveryState))


    case UserRegistered(username) =>
      log.info(s"Administration has recovered a user with username: $username")
      val user = context.child(username).getOrElse(context.actorOf(User.props(username,
                                                               administrationRecoveryState.currentUserIndex), username))

      administrationRecoveryState = administrationRecoveryState.copy(
        users = administrationRecoveryState.users + (username -> (administrationRecoveryState.currentUserIndex , user)),
        currentUserIndex = administrationRecoveryState.currentUserIndex + 1)

      context.become(state(administrationRecoveryState))
  }


  // Methods to process CRUD Commands
  def processGetCommand(getCommand: GetCommand, administrationState: AdministrationState): Unit = {
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByGetCommand(getCommand, administrationState)

    actorRefOption match {
      case Some((_, actorRef)) =>
        actorRef.forward(getCommand)
      case None =>
        val getResponse: GetResponse = getGetResponseByGetCommand(getCommand)
        sender() ! getResponse
    }
  }

  def processRegisterCommand(registerCommand: RegisterCommand, administrationState: AdministrationState): Unit = {
    val identifier: String = getIdentifierByRegisterCommand(registerCommand)
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByRegisterCommand(registerCommand, identifier,
                                                                            administrationState)
    actorRefOption match {
      case Some(_) =>
        registerCommand match {
          case RegisterRestaurant(_, _) =>
            sender() ! RegisterResponse(Failure(RestaurantExistsException()))
          case RegisterReview(_, _) =>
            sender() ! RegisterResponse(Failure(ReviewExistsException()))
          case RegisterUser(_) =>
            sender() ! RegisterResponse(Failure(UserExistsException()))
        }

      case None =>
        val newActorRef: ActorRef = getNewActorRefByRegisterCommand(context, administrationState, registerCommand,
                                                                 identifier)
        val newStateAdministrationState: AdministrationState = getNewStateByRegisterCommand(registerCommand, newActorRef,
                                                                                        identifier, administrationState)

        persistRegisterCommand(registerCommand, newActorRef, identifier, newStateAdministrationState)
    }
  }

  def processRegisterCommandWithVerifyIds(registerCommand: RegisterCommand,
                                        administrationState: AdministrationState): Unit = {
    verifyIdsOnRegisterCommand(registerCommand, administrationState) match {
      case Success(_) =>
        processRegisterCommand(registerCommand, administrationState)
      case Failure(exception) =>
        sender() ! RegisterResponse(Failure(exception))
    }
  }

  def persistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef, identifier: String,
                           newStateAdministrationState: AdministrationState): Unit = {
    registerCommand match {
      case RegisterRestaurant(_, restaurantInfo) =>
        // sending message to writer.
        writerToIndexDatabase ! WriterToIndexDatabase.Command.RegisterRestaurant(identifier,
                                                 newStateAdministrationState.currentRestaurantIndex - 1, restaurantInfo)

        helperPersistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "restaurant", RestaurantRegistered(identifier))

      case RegisterReview(_, reviewInfo) =>
        // sending message to writer.
        writerToIndexDatabase ! WriterToIndexDatabase.Command.RegisterReview(identifier,
                                                         newStateAdministrationState.currentReviewIndex - 1, reviewInfo)

        helperPersistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "review", ReviewRegistered(identifier))

      case RegisterUser(userInfo) =>
        // sending message to writer.
        writerToIndexDatabase ! WriterToIndexDatabase.Command.RegisterUser(
                                                             newStateAdministrationState.currentUserIndex - 1, userInfo)

        helperPersistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "user", UserRegistered(identifier))
    }
  }

  def helperPersistRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef, identifier: String,
                                 newStateAdministrationState: AdministrationState , actorName: String,
                                 event: Event):Unit = {
    persist(event) { _ =>
      log.info(s"Administration has Registered a $actorName with id: ${identifier}")
      newActorRef.forward(registerCommand)
      context.become(state(newStateAdministrationState))
    }
  }

  def processUpdateCommand(updateCommand: UpdateCommand, administrationState: AdministrationState): Unit = {
    val identifier: String = getIdentifierByUpdateCommand(updateCommand)
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByUpdateCommand(updateCommand, identifier,
                                                                            administrationState)
    actorRefOption match {
      case Some((index, actorRef)) =>
          actorRef.forward(updateCommand)

          updateCommand match {
            case UpdateRestaurant(id, restaurantInfo) =>
              // sending message to writer.
              writerToIndexDatabase ! WriterToIndexDatabase.Command.UpdateRestaurant(id, index, restaurantInfo)
              log.info(s"UpdateRestaurant Command for id: $id has been handle by Administration.")

            case UpdateReview(id, reviewInfo) =>
              // sending message to writer.
              writerToIndexDatabase ! WriterToIndexDatabase.Command.UpdateReview(id, index, reviewInfo)
              log.info(s"UpdateReview Command for id: $id has been handle by Administration.")

            case UpdateUser(userInfo) =>
              // sending message to writer.
              writerToIndexDatabase ! WriterToIndexDatabase.Command.UpdateUser(index, userInfo)
              log.info(s"UpdateUser Command for username: ${userInfo.username} has been handle by Administration.")
          }

      case None =>
        val updateResponse: UpdateResponse = getUpdateResponseFailureByUpdateCommand(updateCommand)
        sender() ! updateResponse
    }
  }

  def processUpdateCommandWithVerifyIds(updateCommand: UpdateCommand,
                                        administrationState: AdministrationState): Unit = {
    verifyIdsOnUpdateCommand(updateCommand, administrationState) match {
      case Success(_) =>
        processUpdateCommand(updateCommand, administrationState)
      case Failure(RestaurantNotFoundException(message)) =>
        sender() ! getUpdateResponseFailureByUpdateCommandWithMessage(updateCommand, message)
      case Failure(ReviewNotFoundException(message)) =>
        sender() ! getUpdateResponseFailureByUpdateCommandWithMessage(updateCommand, message)
      case Failure(UserNotFoundException(message)) =>
        sender() ! getUpdateResponseFailureByUpdateCommandWithMessage(updateCommand, message)
    }
  }

  def processUnregisterCommand(unregisterCommand: UnregisterCommand, administrationState: AdministrationState): Unit = {
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByUnregisterCommand(unregisterCommand, administrationState)
    actorRefOption match {
      case Some((_, actorRef)) =>
        actorRef.forward(unregisterCommand)

        unregisterCommand match {
          case UnregisterRestaurant(id) =>
            // sending message to writer.
            writerToIndexDatabase ! WriterToIndexDatabase.Command.UnregisterRestaurant(id)
            log.info(s"UnregisterRestaurant Command for id: $id has been handle by Administration.")

          case UnregisterReview(id) =>
            // sending message to writer.
            writerToIndexDatabase ! WriterToIndexDatabase.Command.UnregisterReview(id)
            log.info(s"UnregisterReview Command for id: $id has been handle by Administration.")

          case UnregisterUser(username) =>
            // sending message to writer.
            writerToIndexDatabase ! WriterToIndexDatabase.Command.UnregisterUser(username)
            log.info(s"UnregisterUser Command for username: $username has been handle by Administration.")
        }

      case None =>
        unregisterCommand match {
          case UnregisterRestaurant(_) =>
            sender() ! UnregisterResponse(Failure(RestaurantNotFoundException()))
          case UnregisterReview(_) =>
            sender() ! UnregisterResponse(Failure(ReviewNotFoundException()))
          case UnregisterUser(_) =>
            sender() ! UnregisterResponse(Failure(UserNotFoundException()))
        }

    }
  }
}

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
  case class RestaurantCreated(id: String) extends Event
  case class ReviewCreated(id: String) extends Event
  case class UserCreated(username: String) extends Event
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
    // Restaurants CRUD Commands
    case getCommand@GetRestaurant(_) =>
      processGetCommand(getCommand, administrationState)

    case GetStarsByRestaurant(restaurantId) =>
      log.info("Administration receive a GetStartByRestaurant Command.")
      readerStarsByRestaurant.forward(ReaderStarsByRestaurant.Command.GetStarsByRestaurant(restaurantId))

    case GetAllRestaurant(pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetAllRestaurant command.")
      readerGetAll.forward(ReaderGetAll.Command.GetAllRestaurant(pageNumber, numberOfElementPerPage))

    case createCommand@CreateRestaurant(_, _) =>
      processCreateCommandWithVerifyIds(createCommand, administrationState)

    case updateCommand@UpdateRestaurant(_, _) =>
      processUpdateCommandWithVerifyIds(updateCommand, administrationState)

    case deleteCommand@DeleteRestaurant(_) =>
      processDeleteCommand(deleteCommand, administrationState)

    // Reviews CRUD Commands
    case getCommand@GetReview(_) =>
      processGetCommand(getCommand, administrationState)

    case GetAllReview(pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetAllReview command.")
      readerGetAll.forward(ReaderGetAll.Command.GetAllReview(pageNumber, numberOfElementPerPage))

    case createCommand@CreateReview(_, _) =>
      processCreateCommandWithVerifyIds(createCommand, administrationState)

    case updateCommand@UpdateReview(_, _) =>
      processUpdateCommandWithVerifyIds(updateCommand, administrationState)

    case deleteCommand@DeleteReview(_) =>
      processDeleteCommand(deleteCommand, administrationState)

    // Users CRUD Commands
    case getCommand@GetUser(_) =>
      processGetCommand(getCommand, administrationState)

    case GetAllUser(pageNumber, numberOfElementPerPage) =>
      log.info("Administration has receive a GetAllUser command.")
      readerGetAll.forward(ReaderGetAll.Command.GetAllUser(pageNumber, numberOfElementPerPage))

    case createCommand@CreateUser(_) =>
      processCreateCommand(createCommand, administrationState)

    case updateCommand@UpdateUser(_) =>
      processUpdateCommand(updateCommand, administrationState)

    case deleteCommand@DeleteUser(_) =>
      processDeleteCommand(deleteCommand, administrationState)


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
    case RestaurantCreated(id) =>
        log.info(s"Administration has recovered a restaurant with id: $id")
        val restaurant = context.child(id).getOrElse(context.actorOf(Restaurant.props(id,
                                                               administrationRecoveryState.currentRestaurantIndex), id))

        administrationRecoveryState = administrationRecoveryState.copy(
          restaurants = administrationRecoveryState.restaurants +
                                               (id -> (administrationRecoveryState.currentRestaurantIndex, restaurant)),
          currentRestaurantIndex = administrationRecoveryState.currentRestaurantIndex + 1)

        context.become(state(administrationRecoveryState))

    case ReviewCreated(id) =>
      log.info(s"Administration has recovered a review with id: $id")
      val review = context.child(id).getOrElse(context.actorOf(Review.props(id,
                                                                   administrationRecoveryState.currentReviewIndex), id))

      administrationRecoveryState = administrationRecoveryState.copy(
        reviews = administrationRecoveryState.reviews + (id -> (administrationRecoveryState.currentReviewIndex, review)),
        currentReviewIndex = administrationRecoveryState.currentReviewIndex + 1)

      context.become(state(administrationRecoveryState))


    case UserCreated(username) =>
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

  def processCreateCommand(createCommand: CreateCommand, administrationState: AdministrationState): Unit = {
    val identifier: String = getIdentifierByCreateCommand(createCommand)
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByCreateCommand(createCommand, identifier,
                                                                            administrationState)
    actorRefOption match {
      case Some(_) =>
        createCommand match {
          case CreateRestaurant(_, _) =>
            sender() ! CreateResponse(Failure(RestaurantExistsException()))
          case CreateReview(_, _) =>
            sender() ! CreateResponse(Failure(ReviewExistsException()))
          case CreateUser(_) =>
            sender() ! CreateResponse(Failure(UserExistsException()))
        }

      case None =>
        val newActorRef: ActorRef = getNewActorRefByCreateCommand(context, administrationState, createCommand,
                                                                 identifier)
        val newStateAdministrationState: AdministrationState = getNewStateByCreateCommand(createCommand, newActorRef,
                                                                                        identifier, administrationState)

        persistCreateCommand(createCommand, newActorRef, identifier, newStateAdministrationState)
    }
  }

  def processCreateCommandWithVerifyIds(createCommand: CreateCommand,
                                        administrationState: AdministrationState): Unit = {
    verifyIdsOnCreateCommand(createCommand, administrationState) match {
      case Success(_) =>
        processCreateCommand(createCommand, administrationState)
      case Failure(exception) =>
        sender() ! CreateResponse(Failure(exception))
    }
  }

  def persistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
                           newStateAdministrationState: AdministrationState): Unit = {
    createCommand match {
      case CreateRestaurant(_, restaurantInfo) =>
        // sending message to writer.
        writerToIndexDatabase ! WriterToIndexDatabase.Command.CreateRestaurant(identifier,
                                                 newStateAdministrationState.currentRestaurantIndex - 1, restaurantInfo)

        helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "restaurant", RestaurantCreated(identifier))

      case CreateReview(_, reviewInfo) =>
        // sending message to writer.
        writerToIndexDatabase ! WriterToIndexDatabase.Command.CreateReview(identifier,
                                                         newStateAdministrationState.currentReviewIndex - 1, reviewInfo)

        helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "review", ReviewCreated(identifier))

      case CreateUser(userInfo) =>
        // sending message to writer.
        writerToIndexDatabase ! WriterToIndexDatabase.Command.CreateUser(
                                                             newStateAdministrationState.currentUserIndex - 1, userInfo)

        helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "user", UserCreated(identifier))
    }
  }

  def helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
                                 newStateAdministrationState: AdministrationState , actorName: String,
                                 event: Event):Unit = {
    persist(event) { _ =>
      log.info(s"Administration has created a $actorName with id: ${identifier}")
      newActorRef.forward(createCommand)
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

  def processDeleteCommand(deleteCommand: DeleteCommand, administrationState: AdministrationState): Unit = {
    val actorRefOption: Option[(Long, ActorRef)] = getActorRefOptionByDeleteCommand(deleteCommand, administrationState)
    actorRefOption match {
      case Some((_, actorRef)) =>
        actorRef.forward(deleteCommand)

        deleteCommand match {
          case DeleteRestaurant(id) =>
            // sending message to writer.
            writerToIndexDatabase ! WriterToIndexDatabase.Command.DeleteRestaurant(id)
            log.info(s"DeleteRestaurant Command for id: $id has been handle by Administration.")

          case DeleteReview(id) =>
            // sending message to writer.
            writerToIndexDatabase ! WriterToIndexDatabase.Command.DeleteReview(id)
            log.info(s"DeleteReview Command for id: $id has been handle by Administration.")

          case DeleteUser(username) =>
            // sending message to writer.
            writerToIndexDatabase ! WriterToIndexDatabase.Command.DeleteUser(username)
            log.info(s"DeleteUser Command for username: $username has been handle by Administration.")
        }

      case None =>
        deleteCommand match {
          case DeleteRestaurant(_) =>
            sender() ! DeleteResponse(Failure(RestaurantNotFoundException()))
          case DeleteReview(_) =>
            sender() ! DeleteResponse(Failure(ReviewNotFoundException()))
          case DeleteUser(_) =>
            sender() ! DeleteResponse(Failure(UserNotFoundException()))
        }

    }
  }
}

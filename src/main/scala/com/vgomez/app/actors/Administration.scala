package com.vgomez.app.actors
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.persistence.PersistentActor
import com.vgomez.app.exception.CustomException._

import scala.util.{Failure, Success}
import com.vgomez.app.actors.abtractions.Abstract.Command._
import com.vgomez.app.actors.abtractions.Abstract.Response._
import com.vgomez.app.actors.abtractions.Abstract.Event._
import com.vgomez.app.actors.AdministrationUtility._
import com.vgomez.app.actors.readers.{ReaderFilterByCategories, ReaderFilterByLocation, ReaderGetAll,
                                      ReaderStarsByRestaurant}
import com.vgomez.app.domain.DomainModel.Location

object Administration {
  // state
  case class AdministrationState(restaurants: Map[String, ActorRef], reviews: Map[String, ActorRef],
                                 users: Map[String, ActorRef])
  // commands
  object Command {
    case class GetStartByRestaurant(restaurantId: String)
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
  // for state recovery
  var administrationRecoveryState = AdministrationState(Map(), Map(), Map())

  override def persistenceId: String = "administration"

  def state(administrationState: AdministrationState): Receive = {
    // Restaurants CRUD Commands
    case getCommand@GetRestaurant(_) =>
      processGetCommand(getCommand, administrationState)

    case GetStartByRestaurant(restaurantId) =>
      log.info("Administration receive a GetStartByRestaurant Command.")
      readerStarsByRestaurant.forward(ReaderStarsByRestaurant.Command.GetStartByRestaurant(restaurantId))

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
        val restaurant = context.child(id).getOrElse(context.actorOf(Restaurant.props(id), id))

        administrationRecoveryState = administrationRecoveryState.copy(
          restaurants = administrationRecoveryState.restaurants + (id -> restaurant))

        context.become(state(administrationRecoveryState))

    case ReviewCreated(id) =>
      log.info(s"Administration has recovered a review with id: $id")
      val review = context.child(id).getOrElse(context.actorOf(Review.props(id), id))

      administrationRecoveryState = administrationRecoveryState.copy(
        reviews = administrationRecoveryState.reviews + (id -> review))

      context.become(state(administrationRecoveryState))


    case UserCreated(username) =>
      log.info(s"Administration has recovered a user with username: $username")
      val user = context.child(username).getOrElse(context.actorOf(User.props(username), username))

      administrationRecoveryState = administrationRecoveryState.copy(
        users = administrationRecoveryState.users + (username -> user))

      context.become(state(administrationRecoveryState))
  }


  // Methods to process CRUD Commands
  def processGetCommand(getCommand: GetCommand, administrationState: AdministrationState): Unit = {
    val actorRefOption: Option[ActorRef] = getActorRefOptionByGetCommand(getCommand, administrationState)

    actorRefOption match {
      case Some(actorRef) =>
        actorRef.forward(getCommand)
      case None =>
        val getResponse: GetResponse = getGetResponseByGetCommand(getCommand)
        sender() ! getResponse
    }
  }

  def processCreateCommand(createCommand: CreateCommand, administrationState: AdministrationState): Unit = {
    val identifier: String = getIdentifierByCreateCommand(createCommand)
    val actorRefOption: Option[ActorRef] = getActorRefOptionByCreateCommand(createCommand, identifier,
                                                                            administrationState)
    actorRefOption match {
      case Some(_) =>
        sender() ! CreateResponse(Failure(IdentifierExistsException()))
      case None =>
        val newActorRef: ActorRef = getNewActorRefByCreateCommand(context, createCommand, identifier)
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
        // sending message to readers.
        readerGetAll ! ReaderGetAll.Command.CreateRestaurant(identifier)
        readerFilterByCategories ! ReaderFilterByCategories.Command.CreateRestaurant(identifier, restaurantInfo.categories)

        helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "restaurant", RestaurantCreated(identifier))

      case CreateReview(_, reviewInfo) =>
        // sending message to readers.
        readerGetAll ! ReaderGetAll.Command.CreateReview(identifier)
        readerStarsByRestaurant ! ReaderStarsByRestaurant.Command.CreateReview(identifier, reviewInfo.restaurantId,
          reviewInfo.stars)

        helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "review", ReviewCreated(identifier))

      case CreateUser(_) =>
        // sending message to reader.
        readerGetAll ! ReaderGetAll.Command.CreateUser(identifier)

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
    val actorRefOption: Option[ActorRef] = getActorRefOptionByUpdateCommand(updateCommand, identifier,
                                                                            administrationState)
    actorRefOption match {
      case Some(actorRef) =>
          actorRef.forward(updateCommand)

          updateCommand match {
            case UpdateRestaurant(id, restaurantInfo) =>
              readerFilterByCategories ! ReaderFilterByCategories.Command.UpdateRestaurant(id,
                                                                                            restaurantInfo.categories)
              log.info(s"UpdateRestaurant Command for id: $id has been handle by Administration.")

            case UpdateReview(id, reviewInfo) =>
              readerStarsByRestaurant ! ReaderStarsByRestaurant.Command.UpdateReview(id, reviewInfo.restaurantId,
                reviewInfo.stars)
              log.info(s"UpdateReview Command for id: $id has been handle by Administration.")

            case UpdateUser(userInfo) => log.info(s"UpdateUser Command for username: ${userInfo.username} " +
              s"has been handle by Administration.")
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
      case Failure(IdentifierNotFoundException(message)) =>
        sender() ! getUpdateResponseFailureByUpdateCommandWithMessage(updateCommand, message)
    }
  }

  def processDeleteCommand(deleteCommand: DeleteCommand, administrationState: AdministrationState): Unit = {
    val actorRefOption: Option[ActorRef] = getActorRefOptionByDeleteCommand(deleteCommand, administrationState)
    actorRefOption match {
      case Some(actorRef) =>
        actorRef.forward(deleteCommand)
      case None =>
        sender() ! DeleteResponse(Failure(IdentifierNotFoundException()))
    }
  }

}

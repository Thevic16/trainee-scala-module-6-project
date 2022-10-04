package com.vgomez.app.actors
import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.PersistentActor


import com.vgomez.app.exception.CustomException._
import scala.util.Failure
import com.vgomez.app.actors.commands.Abstract.Command._
import com.vgomez.app.actors.commands.Abstract.Response._
import com.vgomez.app.actors.commands.Abstract.Event._
import com.vgomez.app.actors.AdministrationUtility._

object Administration {
  // state
  case class AdministrationState(restaurants: Map[String, ActorRef], reviews: Map[String, ActorRef],
                                 users: Map[String, ActorRef])

  // commands
  object Command {
    case object GetAllRestaurants
    case object GetAllReviews
    case object GetAllUsers
  }


  // events
  case class RestaurantCreated(id: String) extends Event
  case class ReviewCreated(id: String) extends Event
  case class UserCreated(username: String) extends Event

}

class Administration extends PersistentActor with ActorLogging{
  import Administration._

  // Commands
  import Restaurant.Command._
  import Review.Command._
  import User.Command._

  // Responses
  import Restaurant.Response._
  import Review.Response._
  import User.Response._

  // state
  var administrationRecoveryState = AdministrationState(Map(), Map(), Map())

  override def persistenceId: String = "administration"

  def state(administrationState: AdministrationState): Receive = {
    // Restaurants Commands
    case getCommand@GetRestaurant(_) =>
      processGetCommand(getCommand, administrationState)

    case createCommand@CreateRestaurant(_, _) =>
      processCreateCommand(createCommand, administrationState)

    case updateCommand@UpdateRestaurant(_, _) =>
      processUpdateCommand(updateCommand, administrationState)

    case deleteCommand@DeleteRestaurant(_) =>
      processDeleteCommand(deleteCommand, administrationState)

    // Reviews Commands
    case getCommand@GetReview(_) =>
      processGetCommand(getCommand, administrationState)

    case createCommand@CreateReview(_, _) =>
      processCreateCommand(createCommand, administrationState)


    case updateCommand@UpdateReview(_, _) =>
      processUpdateCommand(updateCommand, administrationState)

    case deleteCommand@DeleteReview(_) =>
      processDeleteCommand(deleteCommand, administrationState)

    // Users Commands
    case getCommand@GetUser(_) =>
      processGetCommand(getCommand, administrationState)

    case createCommand@CreateUser(_) =>
      processCreateCommand(createCommand, administrationState)

    case updateCommand@UpdateUser(_) =>
      processUpdateCommand(updateCommand, administrationState)

    case deleteCommand@DeleteUser(_) =>
      processDeleteCommand(deleteCommand, administrationState)
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

  override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
    log.error("Administration failed at recovery")
    super.onRecoveryFailure(cause, event)
  }

  // Auxiliary methods

  // Process CRUD Commands
  def processGetCommand(getCommand: GetCommand, administrationState: AdministrationState) = {
    val actorRefOption: Option[ActorRef] = getActorRefOptionByGetCommand(getCommand, administrationState)

    actorRefOption match {
      case Some(actorRef) =>
        actorRef.forward(getCommand)
      case None =>
        val getResponse: GetResponse = getGetResponseByGetCommand(getCommand)
        sender() ! getResponse
    }
  }

  def processCreateCommand(createCommand: CreateCommand, administrationState: AdministrationState) = {
    val identifier: String = getIdentifierByCreateCommand(createCommand)
    val actorRefOption: Option[ActorRef] = getActorRefOptionByCreateCommand(createCommand, identifier,
                                                                            administrationState)
    actorRefOption match {
      case Some(_) =>
        sender() ! CreateResponse(Failure(IdentifierExistsException))
      case None =>
        val newActorRef: ActorRef = getNewActorRefByCreateCommand(createCommand, identifier)
        val newStateAdministrationState: AdministrationState = getNewStateByCreateCommand(createCommand, newActorRef,
                                                                                        identifier, administrationState)

        persistCreateCommand(createCommand, newActorRef, identifier, newStateAdministrationState)
    }
  }

  def getNewActorRefByCreateCommand(createCommand: CreateCommand, identifier: String): ActorRef = {
    createCommand match {
      case CreateRestaurant(_, _) => context.actorOf(Restaurant.props(identifier), identifier)
      case CreateReview(_, _) => context.actorOf(Review.props(identifier), identifier)
      case CreateUser(_) => context.actorOf(User.props(identifier), identifier)
    }
  }

  def persistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
                           newStateAdministrationState: AdministrationState) = {
    createCommand match {
      case CreateRestaurant(_, _) =>
        helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "restaurant", RestaurantCreated(identifier))

      case CreateReview(_, _) =>
        helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "review", ReviewCreated(identifier))

      case CreateUser(_) =>
        helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
          newStateAdministrationState: AdministrationState, "user", UserCreated(identifier))
    }
  }

  def helperPersistCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
                                 newStateAdministrationState: AdministrationState , actorName: String,
                                 event: Event) = {
    persist(event) { _ =>
      log.info(s"Administration has created a $actorName with id: ${identifier}")
      newActorRef.forward(createCommand)
      context.become(state(newStateAdministrationState))
    }
  }

  def processUpdateCommand(updateCommand: UpdateCommand, administrationState: AdministrationState) = {
    val identifier: String = getIdentifierByUpdateCommand(updateCommand)

    val actorRefOption: Option[ActorRef] = getActorRefOptionByUpdateCommand(updateCommand, identifier,
                                                                            administrationState)
    actorRefOption match {
      case Some(actorRef) =>
        actorRef.forward(updateCommand)
      case None =>
        val updateResponse: UpdateResponse = getUpdateResponseByUpdateCommand(updateCommand)
        sender() ! updateResponse
    }
  }

  def processDeleteCommand(deleteCommand: DeleteCommand, administrationState: AdministrationState) = {
    val actorRefOption: Option[ActorRef] = getActorRefOptionByDeleteCommand(deleteCommand,
      administrationState)
    actorRefOption match {
      case Some(actorRef) =>
        actorRef.forward(deleteCommand)
      case None =>
        sender() ! DeleteResponse(Failure(IdentifierNotFoundException))
    }
  }

}

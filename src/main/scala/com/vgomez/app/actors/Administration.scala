package com.vgomez.app.actors
import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.PersistentActor


import java.util.UUID
import com.vgomez.app.erros.CustomError._

import scala.util.{Failure, Success}

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
  case class RestaurantCreated(id: String)
  case class ReviewCreated(id: String)
  case class UserCreated(username: String)

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
    case getCommand@GetRestaurant(id) =>
      administrationState.restaurants.get(id) match {
        case Some(restaurant) =>
          restaurant.forward(getCommand)
        case None =>
          sender() ! GetRestaurantResponse(None, None)
      }

    case createCommand@CreateRestaurant(maybeId, _) =>
      val id = maybeId.getOrElse(UUID.randomUUID().toString)

      administrationState.restaurants.get(id) match {
        case Some(_) =>
          sender() ! CreateRestaurantResponse(Failure(IdentifierExistsException))

        case None =>
          val newRestaurant = context.actorOf(Restaurant.props(id), id)
          val newState = administrationState.copy(restaurants =
            administrationState.restaurants + (id -> newRestaurant))

          persist(RestaurantCreated(id)) { _ =>
            log.info(s"Administration has created a restaurant with id: $id")
            newRestaurant.forward(createCommand)
            context.become(state(newState))
          }
      }


    case updateCommand@UpdateRestaurant(id, _) =>
       administrationState.restaurants.get(id) match {
        case Some(restaurant) =>
          restaurant.forward(updateCommand)

        case None =>
          sender() ! UpdateRestaurantResponse(Failure(IdentifierNotFoundException))
      }

    case deleteCommand@DeleteRestaurant(id) =>
      administrationState.restaurants.get(id) match {
        case Some(restaurant) =>
            restaurant.forward(deleteCommand)

        case None =>
          sender() ! UpdateRestaurantResponse(Failure(IdentifierNotFoundException))
      }

    // Reviews Commands
    case getCommand@GetReview(id) =>
      administrationState.reviews.get(id) match {
        case Some(review) =>
          review.forward(getCommand)
        case None =>
          sender() ! GetReviewResponse(None)
      }

    case createCommand@CreateReview(maybeId, _) =>
      val id = maybeId.getOrElse(UUID.randomUUID().toString)

      administrationState.reviews.get(id) match {
        case Some(_) =>
          sender() ! CreateReviewResponse(Failure(IdentifierExistsException))
        case None =>
          val newReview = context.actorOf(Review.props(id), id)
          val newState = administrationState.copy(reviews =
            administrationState.reviews + (id -> newReview))

          persist(ReviewCreated(id)) { _ =>
            log.info(s"Administration has created a review with id: $id")
            newReview.forward(createCommand)
            context.become(state(newState))
          }
      }


    case updateCommand@UpdateReview(id, _) =>
      administrationState.reviews.get(id) match {
        case Some(review) =>
          review.forward(updateCommand)

        case None =>
          sender() ! UpdateReviewResponse(Failure(IdentifierNotFoundException))
      }

    case deleteCommand@DeleteReview(id) =>
      administrationState.reviews.get(id) match {
        case Some(review) =>
          review.forward(deleteCommand)

        case None =>
          sender() ! UpdateReviewResponse(Failure(IdentifierNotFoundException))
      }

    // Users Commands
    case getCommand@GetUser(username) =>
      log.info(s"Administration receive a GetUser Command")

      administrationState.users.get(username) match {
        case Some(user) =>
          user.forward(getCommand)
        case None =>
          sender() ! GetUserResponse(None)
      }

    case createCommand@CreateUser(userInfo) =>
      administrationState.users.get(userInfo.username) match {
        case Some(_) =>
          sender() ! CreateUserResponse(Failure(UsernameExistsException))
        case None =>
          val newUser = context.actorOf(User.props(userInfo.username), userInfo.username)
          val newState = administrationState.copy(users =
            administrationState.users + (userInfo.username -> newUser))

          persist(UserCreated(userInfo.username)) { _ =>
            log.info(s"Administration has created a user with username: ${userInfo.username}")
            newUser.forward(createCommand)
            context.become(state(newState))
          }
      }

    case updateCommand@UpdateUser(userInfo) =>
      administrationState.users.get(userInfo.username) match {
        case Some(user) =>
          user.forward(updateCommand)

        case None =>
          sender() ! UpdateUserResponse(Failure(IdentifierNotFoundException))
      }

    case deleteCommand@DeleteUser(username) =>
      administrationState.users.get(username) match {
        case Some(user) =>
          user.forward(deleteCommand)

        case None =>
          sender() ! UpdateReviewResponse(Failure(IdentifierNotFoundException))
      }

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

//  def getAllRestaurants(restaurants: List[ActorRef]): List[GetRestaurantResponse] = {
//    def go(restaurants: List[ActorRef], acc: List[GetRestaurantResponse]) = {
//
//    }
//  }

}

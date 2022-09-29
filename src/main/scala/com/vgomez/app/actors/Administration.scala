package com.vgomez.app.actors
import akka.actor.ActorRef
import akka.persistence.PersistentActor
import java.util.UUID
import com.vgomez.app.erros.CustomError._
import scala.util.Failure

object Administration {
  // state
  case class AdministrationState(restaurants: Map[String, ActorRef], reviews: Map[String, ActorRef],
                                 users: Map[String, ActorRef])

  // events
  case class RestaurantCreated(administrationState: AdministrationState)
  case class ReviewCreated(administrationState: AdministrationState)
  case class UserCreated(administrationState: AdministrationState)

}

class Administration extends PersistentActor{
  import Administration._

  // Commands
  import Restaurant.Command._
  import Review.Command._
  import User.Command._

  // Responses
  import Restaurant.Response._
  import Review.Response._
  import User.Response._

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

    case createCommand@CreateRestaurant(_) =>
      val id = UUID.randomUUID().toString
      val newRestaurant = context.actorOf(Restaurant.props(id), id)
      val newState = administrationState.copy(restaurants =
        administrationState.restaurants + (id, newRestaurant))

      persist(RestaurantCreated(newState)){ _ =>
        newRestaurant.forward(createCommand)
        context.become(state(newState))
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

    case createCommand@CreateReview(_) =>
      val id = UUID.randomUUID().toString
      val newReview = context.actorOf(Review.props(id), id)
      val newState = administrationState.copy(reviews =
        administrationState.reviews + (id, newReview))

      persist(ReviewCreated(newState)) { _ =>
        newReview.forward(createCommand)
        context.become(state(newState))
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
            administrationState.users + (userInfo.username, newUser))

          persist(UserCreated(newState)) { _ =>
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

  override def receiveCommand: Receive = state(AdministrationState(Map(), Map(), Map()))

  override def receiveRecover: Receive = {
    case RestaurantCreated(administrationState) =>
        context.become(state(administrationState))

    case ReviewCreated(administrationState) =>
      context.become(state(administrationState))

    case UserCreated(administrationState) =>
      context.become(state(administrationState))
  }
  
}

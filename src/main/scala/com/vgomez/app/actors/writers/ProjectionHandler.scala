package com.vgomez.app.actors.writers

import akka.Done
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.vgomez.app.actors.messages.AbstractMessage.Event.Event
import com.vgomez.app.actors.Restaurant.{RegisterRestaurantState, RestaurantRegistered, RestaurantUnregistered, RestaurantUpdated, UnregisterRestaurantState}
import com.vgomez.app.actors.Review.{RegisterReviewState, ReviewRegistered, ReviewUnregistered, ReviewUpdated, UnregisterReviewState}
import com.vgomez.app.actors.User.{RegisterUserState, UnregisterUserState, UserRegistered, UserUnregistered, UserUpdated}
import com.vgomez.app.data.projectionDatabase.Operation._
import com.vgomez.app.actors.writers.ProjectionHandlerUtility._

import scala.concurrent.Future

/*
Todo #2P
  Description: Use projections to persist events on projection-db (Postgres).
  Action: Create a ProjectionHandler class.
  Status: Done
  Reported by: Sebastian Oliveri.
*/
class ProjectionHandler extends Handler[EventEnvelope[Event]](){
  override def process(envelope: EventEnvelope[Event]): Future[Done] = {
    val processed = envelope.event match {
      // Restaurant Events
      case RestaurantRegistered(restaurantState) =>
        restaurantState match {
          case registerRestaurantState@RegisterRestaurantState(_, _, _, _, _, _, _, _, _, _) =>
            registerRestaurantModel(getRestaurantModelByRegisterRestaurantState(registerRestaurantState))
          case UnregisterRestaurantState =>
            skipEvent
        }

      case RestaurantUpdated(restaurantState) =>
        restaurantState match {
          case registerRestaurantState@RegisterRestaurantState(id, _, _, _, _, _, _, _, _, _) =>
            updateRestaurantModel(id, getRestaurantModelByRegisterRestaurantState(registerRestaurantState))
          case UnregisterRestaurantState =>
            skipEvent
        }

      case RestaurantUnregistered(restaurantState) =>
        restaurantState match {
          case RegisterRestaurantState(id, _, _, _, _, _, _, _, _, _) =>
            unregisterRestaurantModel(id)
          case UnregisterRestaurantState =>
            skipEvent
        }

      // Review Events
      case ReviewRegistered(reviewState) =>
        reviewState match {
          case registerReviewState@RegisterReviewState(_, _, _, _, _, _, _) =>
            registerReviewModel(getReviewModelByRegisterReviewState(registerReviewState))
          case UnregisterReviewState =>
            skipEvent
        }

      case ReviewUpdated(reviewState) =>
        reviewState match {
          case registerReviewState@RegisterReviewState(id, _, _, _, _, _, _) =>
            updateReviewModel(id, getReviewModelByRegisterReviewState(registerReviewState))
          case UnregisterReviewState =>
            skipEvent
        }

      case ReviewUnregistered(reviewState) =>
        reviewState match {
          case RegisterReviewState(id, _, _, _, _, _, _) =>
            unregisterReviewModel(id)
          case UnregisterReviewState =>
            skipEvent
        }

      // User Events
      case UserRegistered(userState) =>
        userState match {
          case registerUserState@RegisterUserState(_, _, _, _, _, _) =>
            registerUserModel(getUserModelByRegisterUserState(registerUserState))
          case UnregisterUserState =>
            skipEvent
        }

      case UserUpdated(userState) =>
        userState match {
          case registerUserState@RegisterUserState(username, _, _, _, _, _) =>
            updateUserModel(username, getUserModelByRegisterUserState(registerUserState))
          case UnregisterUserState =>
            skipEvent
        }

      case UserUnregistered(userState) =>
        userState match {
          case RegisterUserState(username, _, _, _, _, _) =>
            unregisterUserModel(username)
          case UnregisterUserState =>
            skipEvent
        }
    }

    processed
  }

  def skipEvent: Future[Done.type] = {
    Future.successful(Done)
  }
}

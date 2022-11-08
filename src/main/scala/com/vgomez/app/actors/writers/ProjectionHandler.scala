
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.actors.writers

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.vgomez.app.actors.Restaurant._
import com.vgomez.app.actors.Review._
import com.vgomez.app.actors.User._
import com.vgomez.app.actors.messages.AbstractMessage.Event.EventEntity
import com.vgomez.app.actors.writers.ProjectionHandlerUtility._
import com.vgomez.app.data.projection.Operation._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


class ProjectionHandler(system: ActorSystem[_]) extends Handler[EventEnvelope[EventEntity]]() {
  private val log = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext = system.executionContext


  override def process(envelope: EventEnvelope[EventEntity]): Future[Done] = {
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

      case RestaurantUnregistered(id, restaurantState) =>
        restaurantState match {
          case RegisterRestaurantState(_, _, _, _, _, _, _, _, _, _) =>
            unregisterRestaurantModel(id)
          case UnregisterRestaurantState =>
            unregisterRestaurantModel(id)
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

      case ReviewUnregistered(id, reviewState) =>
        reviewState match {
          case RegisterReviewState(id, _, _, _, _, _, _) =>
            unregisterReviewModel(id)
          case UnregisterReviewState =>
            unregisterReviewModel(id)
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

      case UserUnregistered(username, userState) =>
        userState match {
          case RegisterUserState(_, _, _, _, _, _) =>
            unregisterUserModel(username)
          case UnregisterUserState =>
            unregisterUserModel(username)
        }
    }

    processed.onComplete {
      case Success(_) => logEventCount(envelope.event)
      case Failure(e) => log.error(s"A Error has happen during projection processed with message: ${e
        .getMessage}")
    }

    processed
  }

  private def skipEvent: Future[Done.type] = {
    Future.successful(Done)
  }

  private def logEventCount(event: EventEntity): Unit = event match {
    case _: EventEntity =>
      log.info("A new event have been projected to projection database.")
    case _ => ()
  }

}

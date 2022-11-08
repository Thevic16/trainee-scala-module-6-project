
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.actors

import akka.Done
import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Event.EventReview
import com.vgomez.app.exception.CustomException.ReviewUnRegisteredException

import scala.util.{Failure, Success}

object Review {
  def props(id: String, index: Long): Props = Props(new Review(id, index))

  // state
  sealed trait ReviewState

  case class ReviewInfo(username: String, restaurantId: String, stars: Int, text: String, date: String)

  case class RegisterReviewState(id: String, index: Long, username: String, restaurantId: String, stars: Int,
    text: String, date: String) extends ReviewState

  // events
  case class ReviewRegistered(ReviewState: ReviewState) extends EventReview

  case class ReviewUpdated(ReviewState: ReviewState) extends EventReview

  case class ReviewUnregistered(id: String, ReviewState: ReviewState) extends EventReview

  case object UnregisterReviewState extends ReviewState

  // commands
  object Command {
    case class GetReview(id: String) extends GetCommand

    case class RegisterReview(maybeId: Option[String], reviewInfo: ReviewInfo) extends RegisterCommand

    case class UpdateReview(id: String, reviewInfo: ReviewInfo) extends UpdateCommand

    case class UnregisterReview(id: String) extends UnregisterCommand
  }

}

class Review(id: String, index: Long) extends PersistentActor with ActorLogging {

  import Review._
  import Command._

  override def persistenceId: String = id

  def state(reviewState: ReviewState): Receive = {
    case GetReview(_) =>
      reviewState match {
        case RegisterReviewState(_, _, _, _, _, _, _) =>
          sender() ! Some(reviewState)
        case UnregisterReviewState =>
          sender() ! None
      }

    case RegisterReview(_, reviewInfo) =>
      val newState: ReviewState = getNewState(reviewInfo)

      persist(ReviewRegistered(newState)) { _ =>
        sender() ! Success(id)
        context.become(state(newState))
      }

    case UpdateReview(_, reviewInfo) =>
      reviewState match {
        case RegisterReviewState(_, _, _, _, _, _, _) =>
          val newState: ReviewState = getNewState(reviewInfo)

          persist(ReviewUpdated(newState)) { _ =>
            sender() ! Success(Done)
            context.become(state(newState))
          }
        case UnregisterReviewState =>
          sender() ! Failure(ReviewUnRegisteredException)
      }

    case UnregisterReview(_) =>
      log.info(s"Review with id $id has receive a UnregisterReview command.")
      reviewState match {
        case RegisterReviewState(_, _, _, _, _, _, _) =>
          val newState: ReviewState = UnregisterReviewState

          persist(ReviewUnregistered(id, newState)) { _ =>
            sender() ! Success(Done)
            context.become(state(newState))
          }
        case UnregisterReviewState =>
          sender() ! Failure(ReviewUnRegisteredException)
      }
  }

  override def receiveCommand: Receive = state(getState())

  override def receiveRecover: Receive = {
    case ReviewRegistered(reviewState) =>
      context.become(state(reviewState))

    case ReviewUpdated(reviewState) =>
      context.become(state(reviewState))

    case ReviewUnregistered(_, reviewState) =>
      context.become(state(reviewState))
  }

  def getState(username: String = "", restaurantId: String = "", stars: Int = 0, text: String = "",
    date: String = ""): ReviewState = {
    RegisterReviewState(id, index, username, restaurantId, stars, text, date)
  }

  def getNewState(reviewInfo: ReviewInfo): ReviewState = {
    getState(reviewInfo.username, reviewInfo.restaurantId, reviewInfo.stars, reviewInfo.text, reviewInfo.date)
  }

}
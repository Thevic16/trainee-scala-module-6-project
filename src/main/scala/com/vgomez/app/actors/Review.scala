package com.vgomez.app.actors
import akka.Done
import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor

import scala.util.{Failure, Success}
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Event.Event
import com.vgomez.app.actors.messages.AbstractMessage.Response._
import com.vgomez.app.exception.CustomException.ReviewUnRegisteredException


object Review {
  case class ReviewInfo(username: String, restaurantId: String, stars: Int, text: String, date: String)

  // state
  /*
  Todo #2 part 3
    Description: Change Null pattern abstract class for trait.
    Status: Done
    Reported by: Sebastian Oliveri.
  */
  sealed trait ReviewState
  case class RegisterReviewState(id: String, index: Long, username: String, restaurantId: String, stars: Int,
                                 text: String, date: String) extends ReviewState

  case object UnregisterReviewState extends ReviewState

  // commands
  object Command {
    case class GetReview(id: String) extends GetCommand
    case class RegisterReview(maybeId: Option[String], reviewInfo: ReviewInfo) extends RegisterCommand
    case class UpdateReview(id: String, reviewInfo: ReviewInfo) extends UpdateCommand
    case class UnregisterReview(id: String) extends UnregisterCommand
  }

  // events
  case class ReviewRegistered(ReviewState: ReviewState) extends Event
  case class ReviewUpdated(ReviewState: ReviewState) extends Event
  case class ReviewUnregistered(ReviewState: ReviewState) extends Event


  // responses
  object Response {
    case class GetReviewResponse(maybeReviewState: Option[ReviewState]) extends GetResponse
  }

  def props(id: String, index: Long): Props =  Props(new Review(id, index))

}

class Review(id: String, index: Long) extends PersistentActor with ActorLogging{
  import Review._
  import Command._
  import Response._

  override def persistenceId: String = id

  def state(reviewState: ReviewState): Receive = {
    case GetReview(_) =>
      reviewState match {
        case RegisterReviewState(_, _, _, _, _, _, _) =>
          sender() ! GetReviewResponse(Some(reviewState))
        case UnregisterReviewState =>
          sender() ! GetReviewResponse(None)
      }

    case RegisterReview(_, reviewInfo) =>
      val newState: ReviewState = getNewState(reviewInfo)

      persist(ReviewUnregistered(newState)) { _ =>
        sender() ! RegisterResponse(Success(id))
        context.become(state(newState))
      }

    case UpdateReview(_, reviewInfo) =>
      reviewState match {
        case RegisterReviewState(_, _, _, _, _, _, _) =>
          val newState: ReviewState = getNewState(reviewInfo)

          persist(ReviewUpdated(newState)) { _ =>
            sender() ! UpdateResponse(Success(Done))
            context.become(state(newState))
          }
        case UnregisterReviewState =>
          sender() ! UpdateResponse(Failure(ReviewUnRegisteredException))
      }

    case UnregisterReview(_) =>
      log.info(s"Review with id $id has receive a UnregisterReview command.")
      reviewState match {
        case RegisterReviewState(_, _, _, _, _, _, _) =>
          val newState: ReviewState = UnregisterReviewState

          persist(ReviewUnregistered(newState)) { _ =>
            sender() ! UnregisterResponse(Success(Done))
            context.become(state(newState))
          }
        case UnregisterReviewState =>
          sender() ! UnregisterResponse(Failure(ReviewUnRegisteredException))
      }
  }

  override def receiveCommand: Receive = state(getState())

  override def receiveRecover: Receive = {
    case ReviewRegistered(reviewState) =>
      context.become(state(reviewState))

    case ReviewUpdated(reviewState) =>
      context.become(state(reviewState))

    case ReviewUnregistered(reviewState) =>
      context.become(state(reviewState))
  }

  def getState(username: String = "", restaurantId: String = "", stars: Int = 0, text: String = "",
               date: String = ""): ReviewState = {
    RegisterReviewState(id, index, username, restaurantId, stars, text, date)
  }

  def getNewState(reviewInfo: ReviewInfo): ReviewState = {
    getState(reviewInfo.username,reviewInfo.restaurantId, reviewInfo.stars, reviewInfo.text, reviewInfo.date)
  }

}
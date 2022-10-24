package com.vgomez.app.actors
import akka.Done
import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor

import scala.util.{Failure, Success}
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Response._
import com.vgomez.app.exception.CustomException.EntityIsDeletedException


object Review {
  case class ReviewInfo(username: String, restaurantId: String, stars: Int, text: String, date: String)

  // state
  sealed abstract class ReviewState
  case class RegisterReviewState(id: String, index: Long, username: String, restaurantId: String, stars: Int,
                                 text: String, date: String) extends ReviewState

  case object UnregisterReviewState extends ReviewState

  // commands
  object Command {
    case class GetReview(id: String) extends GetCommand
    case class CreateReview(maybeId: Option[String], reviewInfo: ReviewInfo) extends CreateCommand
    case class UpdateReview(id: String, reviewInfo: ReviewInfo) extends UpdateCommand
    case class DeleteReview(id: String) extends DeleteCommand
  }

  // events
  case class ReviewCreated(ReviewState: ReviewState)
  case class ReviewUpdated(ReviewState: ReviewState)
  case class ReviewDeleted(ReviewState: ReviewState)


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

    case CreateReview(_, reviewInfo) =>
      val newState: ReviewState = getNewState(reviewInfo)

      persist(ReviewCreated(newState)) { _ =>
        sender() ! CreateResponse(Success(id))
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
          sender() ! UpdateResponse(Failure(EntityIsDeletedException))
      }

    case DeleteReview(_) =>
      log.info(s"Review with id $id has receive a DeleteReview command.")
      reviewState match {
        case RegisterReviewState(_, _, _, _, _, _, _) =>
          val newState: ReviewState = UnregisterReviewState

          persist(ReviewDeleted(newState)) { _ =>
            sender() ! DeleteResponse(Success(Done))
            context.become(state(newState))
          }
        case UnregisterReviewState =>
          sender() ! DeleteResponse(Failure(EntityIsDeletedException))
      }
  }

  override def receiveCommand: Receive = state(getState())

  override def receiveRecover: Receive = {
    case ReviewCreated(reviewState) =>
      context.become(state(reviewState))

    case ReviewUpdated(reviewState) =>
      context.become(state(reviewState))

    case ReviewDeleted(reviewState) =>
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
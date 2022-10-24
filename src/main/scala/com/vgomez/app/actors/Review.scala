package com.vgomez.app.actors
import akka.Done
import akka.actor.Props
import akka.persistence.PersistentActor

import scala.util.{Failure, Success}
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Response._
import com.vgomez.app.exception.CustomException.EntityIsDeletedException


object Review {

  // state
  case class ReviewInfo(username: String, restaurantId: String, stars: Int, text: String, date: String)

  case class ReviewState(id: String, index: Long, username: String, restaurantId: String, stars: Int, text: String, date: String,
                         isDeleted: Boolean)

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

class Review(id: String, index: Long) extends PersistentActor{
  import Review._
  import Command._
  import Response._

  override def persistenceId: String = id

  def state(reviewState: ReviewState): Receive = {
    case GetReview(_) =>
      if(reviewState.isDeleted){
        sender() ! GetReviewResponse(None)
      }
      else {
        sender() ! GetReviewResponse(Some(reviewState))
      }

    case CreateReview(_, reviewInfo) =>
      val newState: ReviewState = getNewState(reviewInfo)

      persist(ReviewCreated(newState)) { _ =>
        sender() ! CreateResponse(Success(id))
        context.become(state(newState))
      }

    case UpdateReview(_, reviewInfo) =>
      if(reviewState.isDeleted)
        sender() ! UpdateResponse(Failure(EntityIsDeletedException))
      else {
        val newState: ReviewState = getNewState(reviewInfo)

        persist(ReviewUpdated(newState)) { _ =>
          sender() ! UpdateResponse(Success(Done))
          context.become(state(newState))
        }
      }

    case DeleteReview(id) =>
      if (reviewState.isDeleted){
        sender() ! DeleteResponse(Failure(EntityIsDeletedException))
      }
      else {
        val newState: ReviewState = reviewState.copy(isDeleted = true)

        persist(ReviewDeleted(newState)) { _ =>
          sender() ! DeleteResponse(Success(Done))
          context.become(state(newState))
        }
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
    ReviewState(id, index, username, restaurantId, stars, text, date, isDeleted = false)
  }

  def getNewState(reviewInfo: ReviewInfo): ReviewState = {
    getState(reviewInfo.username,reviewInfo.restaurantId, reviewInfo.stars, reviewInfo.text, reviewInfo.date)
  }

}
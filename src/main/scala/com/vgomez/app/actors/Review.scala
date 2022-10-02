package com.vgomez.app.actors
import akka.actor.Props
import akka.persistence.PersistentActor

import scala.util.{Success, Try}


object Review {

  // state
  case class ReviewInfo(userId: String, restaurantId: String, stars: Int, text: String, date: String)

  case class ReviewState(id: String, userId: String, restaurantId: String, stars: Int, text: String, date: String,
                         isDeleted: Boolean)

  // commands
  object Command {
    case class GetReview(id: String)
    case class CreateReview(maybeId: Option[String], reviewInfo: ReviewInfo)
    case class UpdateReview(id: String, reviewInfo: ReviewInfo)
    case class DeleteReview(id: String)
  }

  // events
  case class ReviewCreated(ReviewState: ReviewState)
  case class ReviewUpdated(ReviewState: ReviewState)
  case class ReviewDeleted(ReviewState: ReviewState)


  // responses
  object Response {
    case class GetReviewResponse(maybeReviewState: Option[ReviewState])

    case class CreateReviewResponse(id: String)

    case class UpdateReviewResponse(maybeReviewState: Try[ReviewState])

    case class DeleteReviewResponse(maybeId: Try[String])
  }

  def props(id: String): Props =  Props(new Review(id))

}

class Review(id: String) extends PersistentActor{
  import Review._
  import Command._
  import Response._

  override def persistenceId: String = id

  def state(reviewState: ReviewState): Receive = {
    case GetReview(_) =>
      sender() ! GetReviewResponse(Some(reviewState))

    case CreateReview(_, reviewInfo) =>
      val newState: ReviewState = getNewState(reviewInfo)

      persist(ReviewCreated(newState)) { _ =>
        sender() ! CreateReviewResponse(id)
        context.become(state(newState))
      }

    case UpdateReview(_, reviewInfo) =>
      val newState: ReviewState = getNewState(reviewInfo)

      persist(ReviewUpdated(newState)) { _ =>
        sender() ! UpdateReviewResponse(Success(newState))
        context.become(state(newState))
      }

    case DeleteReview(id) =>
      val newState: ReviewState = reviewState.copy(isDeleted = true)

      persist(ReviewDeleted(newState)) { _ =>
        sender() ! DeleteReviewResponse(Success(id))
        context.become(state(newState))
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

  def getState(userId: String = "", restaurantId: String = "", stars: Int = 0, text: String = "",
               date: String = ""): ReviewState = {
    ReviewState(id, userId, restaurantId, stars, text, date, false)
  }

  def getNewState(reviewInfo: ReviewInfo): ReviewState = {
    getState(reviewInfo.userId,reviewInfo.restaurantId, reviewInfo.stars, reviewInfo.text, reviewInfo.date)
  }

}
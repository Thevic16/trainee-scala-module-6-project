package com.vgomez.app.http
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask

import scala.concurrent.duration._
import akka.util.Timeout
import akka.http.scaladsl.model.headers.Location

import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.vgomez.app.actors.Review._
import com.vgomez.app.actors.Review.Command._
import com.vgomez.app.actors.Review.Response._
import com.vgomez.app.http.HttpResponse._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.erros.CustomError.IdentifierNotFoundException

import scala.util.{Failure, Success}

// Resquest clases
case class ReviewCreationRequest(userId: String, restaurantId: String, stars: Int, text: String, date: String) {
  def toCommand: CreateReview = CreateReview(None, ReviewInfo(userId, restaurantId, stars, text, date))
}

trait ReviewCreationRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val reviewCreationRequestJson = jsonFormat5(ReviewCreationRequest)
}

case class ReviewUpdateRequest(userId: String, restaurantId: String, stars: Int, text: String, date: String) {
  def toCommand(id: String): UpdateReview = UpdateReview(id, ReviewInfo(userId, restaurantId, stars, text, date))
}

trait ReviewUpdateRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val reviewUpdateRequestJson = jsonFormat5(ReviewUpdateRequest)
}

// Response class
case class ReviewResponse(userId: String, restaurantId: String, stars: Int, text: String, date: String)

trait ReviewResponseJsonProtocol extends DefaultJsonProtocol {
  implicit val reviewResponseJson = jsonFormat5(ReviewResponse)
}


// Review Router.
class ReviewRouter(administration: ActorRef)(implicit system: ActorSystem)
  extends ReviewCreationRequestJsonProtocol with ReviewUpdateRequestJsonProtocol
    with ReviewResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  def getReview(id: String): Future[GetReviewResponse] =
    (administration ? GetReview(id)).mapTo[GetReviewResponse]

  def createReview(reviewCreationRequest: ReviewCreationRequest): Future[CreateReviewResponse] =
    (administration ? reviewCreationRequest.toCommand).mapTo[CreateReviewResponse]

  def updateReview(id: String,
                   reviewUpdateRequest: ReviewUpdateRequest): Future[UpdateReviewResponse] =
    (administration ? reviewUpdateRequest.toCommand(id)).mapTo[UpdateReviewResponse]

  def deleteReview(id: String): Future[DeleteReviewResponse] =
    (administration ? DeleteReview(id)).mapTo[DeleteReviewResponse]

  val routes: Route =
    pathPrefix("api" / "reviews"){
      path(Segment) { id =>
        get {
          onSuccess(getReview(id)) {
            case GetReviewResponse(Some(reviewState)) =>
              complete {
                ReviewResponse(reviewState.userId, reviewState.restaurantId, reviewState.stars, reviewState.text,
                  reviewState.date)
              }

            case _ =>
              complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
          }
        } ~
          put {
            entity(as[ReviewUpdateRequest]) { request =>
              onSuccess(updateReview(id, request)) {
                case UpdateReviewResponse(Success(_)) =>
                  respondWithHeader(Location(s"/reviews/$id")) {
                    complete(StatusCodes.OK)
                  }
                case UpdateReviewResponse(Failure(IdentifierNotFoundException)) =>
                  complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
              }
            }
          } ~
          delete {
            onSuccess(deleteReview(id)) {
              case DeleteReviewResponse(Success(_)) =>
                complete(StatusCodes.NoContent)
              case DeleteReviewResponse(Failure(IdentifierNotFoundException)) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
            }
          }
      }~
        pathEndOrSingleSlash {
          post {
            entity(as[ReviewCreationRequest]){ request =>
              onSuccess(createReview(request)){
                case CreateReviewResponse(id) =>
                  respondWithHeader(Location(s"/reviews/$id")){
                    complete(StatusCodes.Created)
                  }
              }
            }
          }
        }
    }
}

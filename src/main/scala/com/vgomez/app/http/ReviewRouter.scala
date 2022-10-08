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
import com.vgomez.app.http.messages.HttpRequest._
import com.vgomez.app.http.messages.HttpResponse._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.actors.Administration.Command.GetAllReview
import com.vgomez.app.exception.CustomException.{IdentifierNotFoundException, ValidationFailException}
import com.vgomez.app.actors.abtractions.Abstract.Response._
import com.vgomez.app.actors.readers.ReaderGetAll.Response.GetAllReviewResponse
import com.vgomez.app.http.validators._

import scala.util.{Failure, Success}

// Review Router.
class ReviewRouter(administration: ActorRef)(implicit system: ActorSystem)
  extends ReviewCreationRequestJsonProtocol with ReviewUpdateRequestJsonProtocol
    with ReviewResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  def getReview(id: String): Future[GetReviewResponse] =
    (administration ? GetReview(id)).mapTo[GetReviewResponse]

  def createReview(reviewCreationRequest: ReviewCreationRequest): Future[CreateResponse] =
    (administration ? reviewCreationRequest.toCommand).mapTo[CreateResponse]

  def updateReview(id: String,
                   reviewUpdateRequest: ReviewUpdateRequest): Future[UpdateReviewResponse] =
    (administration ? reviewUpdateRequest.toCommand(id)).mapTo[UpdateReviewResponse]

  def deleteReview(id: String): Future[DeleteResponse] =
    (administration ? DeleteReview(id)).mapTo[DeleteResponse]

  def getAllReview(pageNumber: Long): Future[GetAllReviewResponse] =
    (administration ? GetAllReview(pageNumber)).mapTo[GetAllReviewResponse]

  val routes: Route =
    pathPrefix("api" / "reviews"){
      path(Segment) { id =>
        get {
          onSuccess(getReview(id)) {
            case GetReviewResponse(Some(reviewState)) =>
              complete {
                ReviewResponse(reviewState.id, reviewState.userId, reviewState.restaurantId, reviewState.stars,
                  reviewState.text, reviewState.date)
              }

            case GetReviewResponse(None) =>
              complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
          }
        } ~
          put {
            entity(as[ReviewUpdateRequest]) { request =>
              ValidatorReviewRequest(request.userId, request.restaurantId, request.stars,
                request.text, request.date).run() match {
                case Success(_) =>
                  onSuccess(updateReview(id, request)) {
                    case UpdateReviewResponse(Success(_)) =>
                      respondWithHeader(Location(s"/reviews/$id")) {
                        complete(StatusCodes.OK)
                      }
                    case UpdateReviewResponse(Failure(_)) =>
                      complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          } ~
          delete {
            onSuccess(deleteReview(id)) {
              case DeleteResponse(Success(_)) =>
                complete(StatusCodes.NoContent)
              case DeleteResponse(Failure(_)) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
            }
          }
      }~
        pathEndOrSingleSlash {
          post {
            entity(as[ReviewCreationRequest]){ request =>
              ValidatorReviewRequest(request.userId, request.restaurantId, request.stars,
                request.text, request.date).run() match {
                case Success(_) =>
                  onSuccess(createReview(request)) {
                    case CreateResponse(id) =>
                      respondWithHeader(Location(s"/reviews/$id")) {
                        complete(StatusCodes.Created)
                      }
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          }
        }
    } ~
      get {
        parameter('pageNumber.as[Long]) { (pageNumber: Long) =>
          onSuccess(getAllReview(pageNumber)) {
            case GetAllReviewResponse(Some(getRestaurantResponses)) => complete {
              getRestaurantResponses.map(getReviewResponseByGetReviewResponse)
            }

            case GetAllReviewResponse(None) =>
              complete(StatusCodes.NotFound, FailureResponse(s"There are not element in this pageNumber."))
          }

        }
      }

  def getReviewResponseByGetReviewResponse(getReviewResponse: GetReviewResponse): ReviewResponse = {
    getReviewResponse match {
      case GetReviewResponse(Some(reviewState)) =>
          ReviewResponse(reviewState.id, reviewState.userId, reviewState.restaurantId, reviewState.stars, reviewState.text,
            reviewState.date)
    }
  }
}

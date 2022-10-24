package com.vgomez.app.http
import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.model.headers.Location

import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.vgomez.app.actors.Review.Command._
import com.vgomez.app.actors.Review.Response._
import com.vgomez.app.http.messages.HttpRequest._
import com.vgomez.app.http.messages.HttpResponse._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.actors.Administration.Command.GetAllReview
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.actors.messages.AbstractMessage.Response._
import com.vgomez.app.actors.readers.ReaderGetAll.Response.GetAllReviewResponse
import com.vgomez.app.http.validators._
import com.vgomez.app.http.RouterUtility._

import scala.util.{Failure, Success}

// Review Router.
class ReviewRouter(administration: ActorRef)(implicit system: ActorSystem, implicit val timeout: Timeout)
  extends ReviewCreationRequestJsonProtocol with ReviewUpdateRequestJsonProtocol
    with ReviewResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher

  def getReview(id: String): Future[GetReviewResponse] =
    (administration ? GetReview(id)).mapTo[GetReviewResponse]

  def createReview(reviewCreationRequest: ReviewCreationRequest): Future[CreateResponse] =
    (administration ? reviewCreationRequest.toCommand).mapTo[CreateResponse]

  def updateReview(id: String,
                   reviewUpdateRequest: ReviewUpdateRequest): Future[UpdateResponse] =
    (administration ? reviewUpdateRequest.toCommand(id)).mapTo[UpdateResponse]

  def deleteReview(id: String): Future[DeleteResponse] =
    (administration ? DeleteReview(id)).mapTo[DeleteResponse]

  def getAllReview(pageNumber: Long, numberOfElementPerPage: Long): Future[GetAllReviewResponse] =
    (administration ? GetAllReview(pageNumber, numberOfElementPerPage)).mapTo[GetAllReviewResponse]

  val routes: Route =
    pathPrefix("api" / "reviews"){
      path(Segment) { id =>
        get {
          onSuccess(getReview(id)) {
            case GetReviewResponse(Some(reviewState)) =>
              complete {
                ReviewResponse(reviewState.id, reviewState.username, reviewState.restaurantId, reviewState.stars,
                  reviewState.text, reviewState.date)
              }

            case GetReviewResponse(None) =>
              complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
          }
        } ~
          put {
            entity(as[ReviewUpdateRequest]) { request =>
              ValidatorReviewRequest(request.username, request.restaurantId, request.stars,
                request.text, request.date).run() match {
                case Success(_) =>
                  onSuccess(updateReview(id, request)) {
                    case UpdateResponse(Success(Done)) =>
                      respondWithHeader(Location(s"/reviews/$id")) {
                        complete(StatusCodes.OK)
                      }
                    case UpdateResponse(Failure(e: RuntimeException)) =>
                      complete(StatusCodes.BadRequest, e.getMessage)
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
              ValidatorReviewRequest(request.username, request.restaurantId, request.stars,
                request.text, request.date).run() match {
                case Success(_) =>
                  onSuccess(createReview(request)) {
                    case CreateResponse(Success(id)) =>
                      respondWithHeader(Location(s"/reviews/$id")) {
                        complete(StatusCodes.Created)
                      }
                    case CreateResponse(Failure(e: RuntimeException)) =>
                      complete(StatusCodes.BadRequest, e.getMessage)
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          } ~
            get {
              parameter('pageNumber.as[Long], 'numberOfElementPerPage.as[Long]) { (pageNumber: Long,
                                                                                   numberOfElementPerPage: Long) =>
                ValidatorRequestWithPagination(pageNumber, numberOfElementPerPage).run() match {
                  case Success(_) =>
                    onSuccess(getAllReview(pageNumber, numberOfElementPerPage)) {
                      case GetAllReviewResponse(Some(getRestaurantResponses)) => complete {
                        getRestaurantResponses.map(getReviewResponseByGetReviewResponse)
                      }

                      case GetAllReviewResponse(None) =>
                        complete(StatusCodes.NotFound, FailureResponse(s"There are not element in this pageNumber."))
                    }
                  case Failure(e: ValidationFailException) =>
                    complete(StatusCodes.BadRequest, FailureResponse(e.message))
                }
              }
            }
        }
    }
}

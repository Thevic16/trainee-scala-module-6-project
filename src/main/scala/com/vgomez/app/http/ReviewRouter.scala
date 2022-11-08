
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.http

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.vgomez.app.actors.Administration.Command.GetAllReview
import com.vgomez.app.actors.Review.Command._
import com.vgomez.app.actors.Review.{RegisterReviewState, ReviewState, UnregisterReviewState}
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.http.RouterUtility.getReviewResponseByReviewState
import com.vgomez.app.http.messages.HttpRequest._
import com.vgomez.app.http.messages.HttpResponse._
import com.vgomez.app.http.validators._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

// Review Router.
class ReviewRouter(administration: ActorRef)(implicit system: ActorSystem, implicit val timeout: Timeout)
  extends ReviewCreationRequestJsonProtocol with ReviewUpdateRequestJsonProtocol
    with ReviewResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport {

  implicit val dispatcher: ExecutionContext = system.dispatcher

  def getReview(id: String): Future[Option[ReviewState]] =
    (administration ? GetReview(id)).mapTo[Option[ReviewState]]

  def registerReview(reviewCreationRequest: ReviewCreationRequest): Future[Try[String]] =
    (administration ? reviewCreationRequest.toCommand).mapTo[Try[String]]

  def updateReview(id: String,
                   reviewUpdateRequest: ReviewUpdateRequest): Future[Try[Done]] =
    (administration ? reviewUpdateRequest.toCommand(id)).mapTo[Try[Done]]

  def unregisterReview(id: String): Future[Try[Done]] =
    (administration ? UnregisterReview(id)).mapTo[Try[Done]]

  def getAllReview(pageNumber: Long, numberOfElementPerPage: Long): Future[Option[List[ReviewState]]] =
    (administration ? GetAllReview(pageNumber, numberOfElementPerPage)).mapTo[Option[List[ReviewState]]]

  val routes: Route =
    pathPrefix("api" / "reviews") {
      path(Segment) { id =>
        get {
          onSuccess(getReview(id)) {
            case Some(reviewState) =>
              reviewState match {
                case RegisterReviewState(id, _, username, restaurantId, stars, text, date) =>
                  complete {
                    ReviewResponse(id, username, restaurantId, stars, text, date)
                  }
                case UnregisterReviewState =>
                  complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
              }

            case None =>
              complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
          }
        } ~
          put {
            entity(as[ReviewUpdateRequest]) { request =>
              ValidatorReviewRequest(request.username, request.restaurantId, request.stars,
                request.text, request.date).run() match {
                case Success(_) =>
                  onSuccess(updateReview(id, request)) {
                    case Success(Done) =>
                      respondWithHeader(Location(s"/reviews/$id")) {
                        complete(StatusCodes.OK)
                      }
                    case Failure(e: RuntimeException) =>
                      complete(StatusCodes.BadRequest, e.getMessage)
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          } ~
          delete {
            onSuccess(unregisterReview(id)) {
              case Success(_) =>
                complete(StatusCodes.NoContent)
              case Failure(_) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Review $id cannot be found"))
            }
          }
      } ~
        pathEndOrSingleSlash {
          post {
            entity(as[ReviewCreationRequest]) { request =>
              ValidatorReviewRequest(request.username, request.restaurantId, request.stars,
                request.text, request.date).run() match {
                case Success(_) =>
                  onSuccess(registerReview(request)) {
                    case Success(id) =>
                      respondWithHeader(Location(s"/reviews/$id")) {
                        complete(StatusCodes.Created)
                      }
                    case Failure(e: RuntimeException) =>
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

                      case Some(listReviewState) => complete {
                        listReviewState.map(getReviewResponseByReviewState)
                      }
                      case None =>
                        complete(StatusCodes.NotFound, FailureResponse("There are not element in this" +
                          " pageNumber."))
                    }
                  case Failure(e: ValidationFailException) =>
                    complete(StatusCodes.BadRequest, FailureResponse(e.message))
                }
              }
            }
        }
    }
}

package com.vgomez.app.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.vgomez.app.actors.Administration.Command.{GetRecommendationCloseToLocation, GetRecommendationCloseToMe}
import com.vgomez.app.actors.messages.AbstractMessage.Response.GetRecommendationResponse
import com.vgomez.app.http.messages.HttpRequest.{GetRecommendationCloseToLocationRequest,
                                                GetRecommendationCloseToLocationRequestJsonProtocol,
                                                GetRecommendationCloseToMeRequest,
                                                GetRecommendationCloseToMeRequestJsonProtocol}
import com.vgomez.app.http.messages.HttpResponse.{FailureResponse, FailureResponseJsonProtocol,
                                                  RestaurantResponseJsonProtocol}
import akka.http.scaladsl.server.Directives._
import com.vgomez.app.domain.DomainModel
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.http.validators.{ValidatorGetRecommendationCloseToLocationRequest,
                                       ValidatorGetRecommendationCloseToMeRequest,
                                        ValidatorRequestWithPagination}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import com.vgomez.app.http.RouterUtility._


class RecommendationLocationsRouter(administration: ActorRef)(implicit system: ActorSystem,
                                                              implicit val timeout: Timeout)
  extends GetRecommendationCloseToLocationRequestJsonProtocol with GetRecommendationCloseToMeRequestJsonProtocol
    with RestaurantResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher

  def getRecommendationCloseToLocation(latitude: Double, longitude: Double,
                                       rangeInKm: Double, pageNumber: Long,
                                       numberOfElementPerPage: Long): Future[GetRecommendationResponse] =
    (administration ? GetRecommendationCloseToLocation(DomainModel.Location(latitude, longitude),
                                                        rangeInKm, pageNumber,
                                                        numberOfElementPerPage)).mapTo[GetRecommendationResponse]

  def getRecommendationCloseToMe(username: String, rangeInKm: Double, pageNumber: Long,
                                 numberOfElementPerPage: Long): Future[GetRecommendationResponse] =
    (administration ? GetRecommendationCloseToMe(username, rangeInKm, pageNumber,
                                                   numberOfElementPerPage)).mapTo[GetRecommendationResponse]

  val routes: Route =
    pathPrefix("api" / "recommendations" / "locations"){
      pathEndOrSingleSlash {
        post {
          parameter('pageNumber.as[Long], 'numberOfElementPerPage.as[Long], 'service.as[String]) {
            (pageNumber: Long, numberOfElementPerPage: Long, service: String) =>
              service match {
                case "close-to-location" =>
                  ValidatorRequestWithPagination(pageNumber, numberOfElementPerPage).run() match {
                    case Success(_) =>
                      entity(as[GetRecommendationCloseToLocationRequest]) { request =>
                        ValidatorGetRecommendationCloseToLocationRequest(request.latitude, request.longitude,
                          request.rangeInKm).run() match {
                          case Success(_) =>
                            onSuccess(getRecommendationCloseToLocation(request.latitude, request.longitude,
                              request.rangeInKm, pageNumber, numberOfElementPerPage)) {
                              case GetRecommendationResponse(Some(getRestaurantResponses)) => complete {
                                getRestaurantResponses.map(getRestaurantResponseByGetRestaurantResponse)
                              }
                              case GetRecommendationResponse(None) =>
                                complete(StatusCodes.NotFound, FailureResponse(s"There are not element in this" +
                                  s" pageNumber."))
                            }
                          case Failure(e: ValidationFailException) =>
                            complete(StatusCodes.BadRequest, FailureResponse(e.message))
                        }
                      }
                    case Failure(e: ValidationFailException) =>
                      complete(StatusCodes.BadRequest, FailureResponse(e.message))
                  }

                case "close-to-me" =>
                  ValidatorRequestWithPagination(pageNumber, numberOfElementPerPage).run() match {
                    case Success(_) =>
                      entity(as[GetRecommendationCloseToMeRequest]) { request =>
                        ValidatorGetRecommendationCloseToMeRequest(request.username, request.rangeInKm).run() match {
                          case Success(_) =>
                            onSuccess(getRecommendationCloseToMe(request.username, request.rangeInKm, pageNumber,
                              numberOfElementPerPage)) {
                              case GetRecommendationResponse(Some(getRestaurantResponses)) => complete {
                                getRestaurantResponses.map(getRestaurantResponseByGetRestaurantResponse)
                              }
                              case GetRecommendationResponse(None) =>
                                complete(StatusCodes.NotFound, FailureResponse(s"There are not element in this" +
                                  s" pageNumber (in case you expect a result check username)."))
                            }
                          case Failure(e: ValidationFailException) =>
                            complete(StatusCodes.BadRequest, FailureResponse(e.message))
                        }
                      }
                    case Failure(e: ValidationFailException) =>
                      complete(StatusCodes.BadRequest, FailureResponse(e.message))
                  }

                case _ =>
                  complete(StatusCodes.BadRequest, FailureResponse(s"Service $service hasn't been found!"))
              }
          }
        }
      }
    }
}

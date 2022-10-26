package com.vgomez.app.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.vgomez.app.actors.Administration.Command.{GetRecommendationFilterByFavoriteCategories,
                                                     GetRecommendationFilterByUserFavoriteCategories}
import com.vgomez.app.actors.messages.AbstractMessage.Response.GetRecommendationResponse
import com.vgomez.app.http.messages.HttpRequest.{GetRecommendationFilterByFavoriteCategoriesRequest,
                                                GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol,
                                                GetRecommendationFilterByUserFavoriteCategoriesRequest,
                                                GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol}
import com.vgomez.app.http.messages.HttpResponse.{FailureResponse, FailureResponseJsonProtocol,
                                                    RestaurantResponseJsonProtocol}
import akka.http.scaladsl.server.Directives._
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.http.validators.{ValidatorGetRecommendationFilterByFavoriteCategoriesRequest,
                                        ValidatorGetRecommendationFilterByUserFavoriteCategoriesRequest,
                                          ValidatorRequestWithPagination}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import com.vgomez.app.http.RouterUtility._


class RecommendationCategoriesRouter(administration: ActorRef)(implicit system: ActorSystem,
                                                               implicit val timeout: Timeout)
  extends GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol
    with GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol with RestaurantResponseJsonProtocol
     with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher

  def getRecommendationFilterByFavoriteCategories(favoriteCategories: Set[String],
                                                  pageNumber: Long,
                                                  numberOfElementPerPage: Long): Future[GetRecommendationResponse] =
    (administration ? GetRecommendationFilterByFavoriteCategories(favoriteCategories, pageNumber,
      numberOfElementPerPage)).mapTo[GetRecommendationResponse]

  def getRecommendationFilterByUserFavoriteCategories(idUser: String,
                                                      pageNumber: Long,
                                                      numberOfElementPerPage: Long): Future[GetRecommendationResponse] =
    (administration ? GetRecommendationFilterByUserFavoriteCategories(idUser, pageNumber,
      numberOfElementPerPage)).mapTo[GetRecommendationResponse]

  /*
  Todo #15
    Description: Generalize recommendation endpoints (use a parameter to access each available service).
    Status: Done
    Reported by: Nafer Sanabria.
  */
  val routes: Route =
    pathPrefix("api" / "recommendations" / "categories"){
        pathEndOrSingleSlash {
          post {
            parameter('pageNumber.as[Long], 'numberOfElementPerPage.as[Long], 'service.as[String]) {
              (pageNumber: Long, numberOfElementPerPage: Long, service: String) =>

                service match {
                  case "filter-by-categories" =>
                    ValidatorRequestWithPagination(pageNumber, numberOfElementPerPage).run() match {
                      case Success(_) =>
                        entity(as[GetRecommendationFilterByFavoriteCategoriesRequest]) { request =>
                          ValidatorGetRecommendationFilterByFavoriteCategoriesRequest(request.favoriteCategories).run() match {
                            case Success(_) =>
                              onSuccess(getRecommendationFilterByFavoriteCategories(request.favoriteCategories,
                                pageNumber, numberOfElementPerPage)) {

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

                  case "filter-by-user-categories" =>
                    ValidatorRequestWithPagination(pageNumber, numberOfElementPerPage).run() match {
                      case Success(_) =>
                        entity(as[GetRecommendationFilterByUserFavoriteCategoriesRequest]) { request =>
                          ValidatorGetRecommendationFilterByUserFavoriteCategoriesRequest(request.username).run() match {
                            case Success(_) =>
                              onSuccess(getRecommendationFilterByUserFavoriteCategories(request.username, pageNumber,
                                numberOfElementPerPage)) {
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

                  case _ =>
                    complete(StatusCodes.BadRequest, FailureResponse(s"Service $service hasn't been found!"))
                }
            }
          }
        }
    }
}


// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.vgomez.app.actors.Administration.Command.{GetRecommendationFilterByFavoriteCategories,
  GetRecommendationFilterByUserFavoriteCategories}
import com.vgomez.app.actors.Restaurant.RestaurantState
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.http.RouterUtility._
import com.vgomez.app.http.messages.HttpRequest.{GetRecommendationFilterByFavoriteCategoriesRequest,
  GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol,
  GetRecommendationFilterByUserFavoriteCategoriesRequest,
  GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol}
import com.vgomez.app.http.messages.HttpResponse.{FailureResponse, FailureResponseJsonProtocol,
  RestaurantResponseJsonProtocol}
import com.vgomez.app.http.validators.{ValidatorGetRecommendationFilterByFavoriteCategoriesRequest,
  ValidatorGetRecommendationFilterByUserFavoriteCategoriesRequest, ValidatorRequestWithPagination}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


class RecommendationCategoriesRouter(administration: ActorRef)(implicit system: ActorSystem,
  implicit val timeout: Timeout)
  extends GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol
    with GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol
    with RestaurantResponseJsonProtocol
    with FailureResponseJsonProtocol with SprayJsonSupport {

  implicit val dispatcher: ExecutionContext = system.dispatcher

  def getRecommendationFilterByFavoriteCategories(favoriteCategories: Set[String],
    pageNumber: Long, numberOfElementPerPage: Long): Future[Option[List[RestaurantState]]] =
    (administration ? GetRecommendationFilterByFavoriteCategories(favoriteCategories, pageNumber,
      numberOfElementPerPage)).mapTo[Option[List[RestaurantState]]]

  def getRecommendationFilterByUserFavoriteCategories(idUser: String, pageNumber: Long,
    numberOfElementPerPage: Long): Future[Option[List[RestaurantState]]] =
    (administration ? GetRecommendationFilterByUserFavoriteCategories(idUser, pageNumber,
      numberOfElementPerPage)).mapTo[Option[List[RestaurantState]]]


  val routes: Route =
    pathPrefix("api" / "recommendations" / "categories") {
      pathEndOrSingleSlash {
        post {
          parameter('pageNumber.as[Long], 'numberOfElementPerPage.as[Long], 'service.as[String]) {
            (pageNumber: Long, numberOfElementPerPage: Long, service: String) =>

              service match {
                case "filter-by-categories" =>
                  ValidatorRequestWithPagination(pageNumber, numberOfElementPerPage).run() match {
                    case Success(_) =>
                      entity(as[GetRecommendationFilterByFavoriteCategoriesRequest]) { request =>
                        ValidatorGetRecommendationFilterByFavoriteCategoriesRequest(
                          request.favoriteCategories).run() match {
                          case Success(_) =>
                            onSuccess(getRecommendationFilterByFavoriteCategories(request.favoriteCategories,
                              pageNumber, numberOfElementPerPage)) {

                              case Some(listRestaurantState) => complete {
                                listRestaurantState.map(getRestaurantResponseByRestaurantState)
                              }
                              case None =>
                                complete(StatusCodes.NotFound, FailureResponse("There are not element" +
                                  " in this pageNumber."))
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
                        ValidatorGetRecommendationFilterByUserFavoriteCategoriesRequest(
                          request.username).run() match {
                          case Success(_) =>
                            onSuccess(getRecommendationFilterByUserFavoriteCategories(request.username,
                              pageNumber, numberOfElementPerPage)) {
                              case Some(listRestaurantState) => complete {
                                listRestaurantState.map(getRestaurantResponseByRestaurantState)
                              }
                              case None =>
                                complete(StatusCodes.NotFound, FailureResponse("There are not element in " +
                                  "this pageNumber (in case you expect a result check username)."))
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

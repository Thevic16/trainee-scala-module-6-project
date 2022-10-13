package com.vgomez.app.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.vgomez.app.actors.Administration.Command.{GetRecommendationFilterByFavoriteCategories,
                                                     GetRecommendationFilterByUserFavoriteCategories}
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse
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
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
import com.vgomez.app.http.RouterUtility._


class RecommendationFilterByCategoriesRouter(administration: ActorRef)(implicit system: ActorSystem)
  extends GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol
    with GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol with RestaurantResponseJsonProtocol
     with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

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

  val routes: Route =
    pathPrefix("api" / "recommendations" / "filter-by-categories"){
        pathEndOrSingleSlash {
          post {
            parameter('pageNumber.as[Long], 'numberOfElementPerPage.as[Long]) { (pageNumber: Long,
                                                                                 numberOfElementPerPage: Long) =>
              ValidatorRequestWithPagination(pageNumber, numberOfElementPerPage).run() match {
                case Success(_) =>
                  entity(as[GetRecommendationFilterByFavoriteCategoriesRequest]) { request =>
                    ValidatorGetRecommendationFilterByFavoriteCategoriesRequest(request.favoriteCategories).run() match {
                      case Success(_) =>
                        onSuccess(getRecommendationFilterByFavoriteCategories(request.favoriteCategories, pageNumber,
                          numberOfElementPerPage)) {

                          case GetRecommendationResponse(Some(getRestaurantResponses)) => complete {
                            getRestaurantResponses.map(getRestaurantResponseByGetRestaurantResponse)
                          }
                          case GetRecommendationResponse(None) =>
                            complete(StatusCodes.NotFound, FailureResponse(s"There are not element in this pageNumber."))

                        }
                      case Failure(e: ValidationFailException) =>
                        complete(StatusCodes.BadRequest, FailureResponse(e.message))
                    }
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          }
        }
    } ~ pathPrefix("api" / "recommendations" / "filter-by-user-categories") {
      pathEndOrSingleSlash {
        post {
          parameter('pageNumber.as[Long], 'numberOfElementPerPage.as[Long]) { (pageNumber: Long,
                                                                               numberOfElementPerPage: Long) =>
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
                          complete(StatusCodes.NotFound, FailureResponse(s"There are not element in this pageNumber."))
                      }
                    case Failure(e: ValidationFailException) =>
                      complete(StatusCodes.BadRequest, FailureResponse(e.message))
                  }
                }
              case Failure(e: ValidationFailException) =>
                complete(StatusCodes.BadRequest, FailureResponse(e.message))
            }
          }
        }
      }
    }
}

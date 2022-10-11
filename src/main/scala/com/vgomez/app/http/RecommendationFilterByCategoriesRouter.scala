package com.vgomez.app.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.vgomez.app.actors.Administration.Command.{GetRecommendationFilterByFavoriteCategories, GetRecommendationFilterByUserFavoriteCategories}
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse
import com.vgomez.app.http.messages.HttpRequest.{GetRecommendationFilterByFavoriteCategoriesRequest, GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol, GetRecommendationFilterByUserFavoriteCategoriesRequest, GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol}
import com.vgomez.app.http.messages.HttpResponse.{FailureResponse, FailureResponseJsonProtocol, RestaurantResponse, RestaurantResponseJsonProtocol}
import akka.http.scaladsl.server.Directives._
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.domain.Transformer.transformScheduleToSimpleScheduler
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.http.validators.{ValidatorGetRecommendationFilterByFavoriteCategoriesRequest, ValidatorGetRecommendationFilterByUserFavoriteCategoriesRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}


class RecommendationFilterByCategoriesRouter(administration: ActorRef)(implicit system: ActorSystem)
  extends GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol
    with GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol with RestaurantResponseJsonProtocol
     with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  def getRecommendationFilterByFavoriteCategories(favoriteCategories: Set[String]): Future[GetRecommendationResponse] =
    (administration ? GetRecommendationFilterByFavoriteCategories(favoriteCategories)).mapTo[GetRecommendationResponse]

  def getRecommendationFilterByUserFavoriteCategories(idUser: String): Future[GetRecommendationResponse] =
    (administration ? GetRecommendationFilterByUserFavoriteCategories(idUser)).mapTo[GetRecommendationResponse]

  val routes: Route =
    pathPrefix("api" / "recommendations" / "filter-by-categories"){
        pathEndOrSingleSlash {
          post {
            entity(as[GetRecommendationFilterByFavoriteCategoriesRequest]){ request =>
              ValidatorGetRecommendationFilterByFavoriteCategoriesRequest(request.favoriteCategories).run() match {
                case Success(_) =>
                  onSuccess(getRecommendationFilterByFavoriteCategories(request.favoriteCategories)) {
                    case GetRecommendationResponse(Some(getRestaurantResponses)) => complete {
                      getRestaurantResponses.map(getRestaurantResponseByGetRestaurantResponse)
                    }
                    case GetRecommendationResponse(None) =>
                      complete(StatusCodes.InternalServerError)
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
          entity(as[GetRecommendationFilterByUserFavoriteCategoriesRequest]) { request =>
            ValidatorGetRecommendationFilterByUserFavoriteCategoriesRequest(request.username).run() match {
              case Success(_) =>
                onSuccess(getRecommendationFilterByUserFavoriteCategories(request.username)) {
                  case GetRecommendationResponse(Some(getRestaurantResponses)) => complete {
                    getRestaurantResponses.map(getRestaurantResponseByGetRestaurantResponse)
                  }
                  case GetRecommendationResponse(None) =>
                    complete(StatusCodes.InternalServerError)
                }
              case Failure(e: ValidationFailException) =>
                complete(StatusCodes.BadRequest, FailureResponse(e.message))
            }
          }
        }
      }
    }

  def getRestaurantResponseByGetRestaurantResponse(getRestaurantResponse: GetRestaurantResponse): RestaurantResponse = {
    getRestaurantResponse match {
      case GetRestaurantResponse(Some(restaurantState), Some(starts)) =>
        RestaurantResponse(restaurantState.id, restaurantState.username, restaurantState.name, restaurantState.state,
          restaurantState.city, restaurantState.postalCode, restaurantState.location.latitude,
          restaurantState.location.longitude, restaurantState.categories,
          transformScheduleToSimpleScheduler(restaurantState.schedule), starts)
    }
  }

}

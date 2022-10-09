package com.vgomez.app.http

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.vgomez.app.actors.Administration.Command.{GetRecommendationCloseToLocation, GetRecommendationCloseToMe, GetRecommendationFilterByFavoriteCategories, GetRecommendationFilterByUserFavoriteCategories}
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse
import com.vgomez.app.http.messages.HttpRequest.{GetRecommendationCloseToLocationRequest, GetRecommendationCloseToLocationRequestJsonProtocol, GetRecommendationCloseToMeRequest, GetRecommendationCloseToMeRequestJsonProtocol, GetRecommendationFilterByFavoriteCategoriesRequest, GetRecommendationFilterByFavoriteCategoriesRequestJsonProtocol, GetRecommendationFilterByUserFavoriteCategoriesRequest, GetRecommendationFilterByUserFavoriteCategoriesRequestJsonProtocol}
import com.vgomez.app.http.messages.HttpResponse.{FailureResponse, FailureResponseJsonProtocol, RestaurantResponse, RestaurantResponseJsonProtocol}
import akka.http.scaladsl.server.Directives._
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.domain.DomainModel
import com.vgomez.app.domain.Transformer.transformScheduleToSimpleScheduler
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.http.validators.{ValidatorGetRecommendationCloseToLocationRequest, ValidatorGetRecommendationCloseToMeRequest, ValidatorGetRecommendationFilterByFavoriteCategoriesRequest, ValidatorGetRecommendationFilterByUserFavoriteCategoriesRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}


class RecommendationFilterByLocationRouter(administration: ActorRef)(implicit system: ActorSystem)
  extends GetRecommendationCloseToLocationRequestJsonProtocol with GetRecommendationCloseToMeRequestJsonProtocol
    with RestaurantResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  def getRecommendationCloseToLocation(latitude: Double, longitude: Double,
                                       rangeInKm: Double): Future[GetRecommendationResponse] =
    (administration ? GetRecommendationCloseToLocation(DomainModel.Location(latitude, longitude),
                                                        rangeInKm)).mapTo[GetRecommendationResponse]

  def getRecommendationCloseToMe(username: String, rangeInKm: Double): Future[GetRecommendationResponse] =
    (administration ? GetRecommendationCloseToMe(username, rangeInKm)).mapTo[GetRecommendationResponse]

  val routes: Route =
    pathPrefix("api" / "recommendations" / "close-to-location"){
      pathEndOrSingleSlash {
        post {
          entity(as[GetRecommendationCloseToLocationRequest]){ request =>
            ValidatorGetRecommendationCloseToLocationRequest(request.latitude, request.longitude, request.rangeInKm).run() match {
              case Success(_) =>
                onSuccess(getRecommendationCloseToLocation(request.latitude, request.longitude, request.rangeInKm)) {
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
    } ~ pathPrefix("api" / "recommendations" / "close-to-me") {
      pathEndOrSingleSlash {
        post {
          entity(as[GetRecommendationCloseToMeRequest]) { request =>
            ValidatorGetRecommendationCloseToMeRequest(request.username, request.rangeInKm).run() match {
              case Success(_) =>
                onSuccess(getRecommendationCloseToMe(request.username, request.rangeInKm)) {
                  case GetRecommendationResponse(Some(getRestaurantResponses)) => complete {
                    getRestaurantResponses.map(getRestaurantResponseByGetRestaurantResponse)
                  }
                  case GetRecommendationResponse(None) =>
                    complete(StatusCodes.BadRequest, FailureResponse("Check username field."))
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
        RestaurantResponse(restaurantState.id, restaurantState.userId, restaurantState.name, restaurantState.state,
          restaurantState.city, restaurantState.postalCode, restaurantState.location.latitude,
          restaurantState.location.longitude, restaurantState.categories,
          transformScheduleToSimpleScheduler(restaurantState.schedule), starts)
    }
  }

}

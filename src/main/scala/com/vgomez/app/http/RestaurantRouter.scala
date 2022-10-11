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
import com.vgomez.app.actors.Restaurant._
import com.vgomez.app.actors.Restaurant.Command._
import com.vgomez.app.actors.Restaurant.Response._
import com.vgomez.app.domain.{DomainModel, SimpleScheduler}
import com.vgomez.app.domain.Transformer._
import com.vgomez.app.http.messages.HttpRequest._
import com.vgomez.app.http.messages.HttpResponse._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.actors.Administration.Command.GetAllRestaurant
import com.vgomez.app.exception.CustomException.{IdentifierNotFoundException, ValidationFailException}
import com.vgomez.app.actors.abtractions.Abstract.Response._
import com.vgomez.app.actors.readers.ReaderGetAll.Response.GetAllRestaurantResponse
import com.vgomez.app.http.validators._

import scala.util.{Failure, Success}

// Restaurant Router.
class RestaurantRouter(administration: ActorRef)(implicit system: ActorSystem)
  extends RestaurantCreationRequestJsonProtocol with RestaurantUpdateRequestJsonProtocol
    with RestaurantResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  def getRestaurant(id: String): Future[GetRestaurantResponse] =
    (administration ? GetRestaurant(id)).mapTo[GetRestaurantResponse]

  def createRestaurant(restaurantCreationRequest: RestaurantCreationRequest): Future[CreateResponse] =
    (administration ? restaurantCreationRequest.toCommand).mapTo[CreateResponse]

  def updateRestaurant(id: String,
                       restaurantUpdateRequest: RestaurantUpdateRequest): Future[UpdateRestaurantResponse] =
    (administration ? restaurantUpdateRequest.toCommand(id)).mapTo[UpdateRestaurantResponse]

  def deleteRestaurant(id: String): Future[DeleteResponse] =
    (administration ? DeleteRestaurant(id)).mapTo[DeleteResponse]

  def getAllRestaurant(pageNumber: Long): Future[GetAllRestaurantResponse] =
    (administration ? GetAllRestaurant(pageNumber)).mapTo[GetAllRestaurantResponse]


  val routes: Route =
    pathPrefix("api" / "restaurants"){
      path(Segment) { id =>
        get {
          onSuccess(getRestaurant(id)) {
            case GetRestaurantResponse(Some(restaurantState), Some(starts)) =>
              complete {
                RestaurantResponse(restaurantState.id, restaurantState.username, restaurantState.name,
                  restaurantState.state, restaurantState.city, restaurantState.postalCode,
                  restaurantState.location.latitude, restaurantState.location.longitude,
                  restaurantState.categories, transformScheduleToSimpleScheduler(restaurantState.schedule), starts)
              }

            case GetRestaurantResponse(None, None) =>
              complete(StatusCodes.NotFound, FailureResponse(s"Restaurant $id cannot be found"))
          }
        } ~
          put {
            entity(as[RestaurantUpdateRequest]) { request =>
              ValidatorRestaurantRequest(request.username, request.name, request.state, request.city, request.postalCode,
                                          request.latitude, request.longitude, request.categories,
                                            request.schedule).run() match {
                case Success(_) =>
                  onSuccess(updateRestaurant(id, request)) {
                    case UpdateRestaurantResponse(Success(_)) =>
                      respondWithHeader(Location(s"/restaurants/$id")) {
                        complete(StatusCodes.OK)
                      }
                    case UpdateRestaurantResponse(Failure(_)) =>
                      complete(StatusCodes.NotFound, FailureResponse(s"Restaurant $id cannot be found"))
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          } ~
          delete {
            onSuccess(deleteRestaurant(id)) {
              case DeleteResponse(Success(_)) =>
                complete(StatusCodes.NoContent)
              case DeleteResponse(Failure(_)) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Restaurant $id cannot be found"))
            }
          }
      }~
      pathEndOrSingleSlash {
        post {
          entity(as[RestaurantCreationRequest]){ request =>
            ValidatorRestaurantRequest(request.username, request.name, request.state, request.city, request.postalCode,
              request.latitude, request.longitude, request.categories,
              request.schedule).run() match {
              case Success(_) =>
                onSuccess(createRestaurant(request)) {
                  case CreateResponse(Success(id)) =>
                    respondWithHeader(Location(s"/restaurants/$id")) {
                      complete(StatusCodes.Created)
                    }
                  case CreateResponse(Failure(_)) =>
                    complete(StatusCodes.InternalServerError)
                }
              case Failure(e: ValidationFailException) =>
                complete(StatusCodes.BadRequest, FailureResponse(e.message))
            }
          }
        }~
          get {
            parameter('pageNumber.as[Long]) { (pageNumber: Long) =>
              onSuccess(getAllRestaurant(pageNumber)) {
                case GetAllRestaurantResponse(Some(getRestaurantResponses)) => complete {
                  getRestaurantResponses.map(getRestaurantResponseByGetRestaurantResponse)
                }

                case GetAllRestaurantResponse(None) =>
                  complete(StatusCodes.NotFound, FailureResponse(s"There are not element in this pageNumber."))
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

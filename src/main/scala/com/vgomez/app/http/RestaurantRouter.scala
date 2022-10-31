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
import com.vgomez.app.actors.Restaurant.Command._
import com.vgomez.app.actors.Restaurant.Response._
import com.vgomez.app.http.messages.HttpRequest._
import com.vgomez.app.http.messages.HttpResponse._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.actors.Administration.Command.{GetAllRestaurant, GetStarsByRestaurant}
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.actors.messages.AbstractMessage.Response._
import com.vgomez.app.actors.readers.ReaderGetAll.Response.GetAllRestaurantResponse
import com.vgomez.app.actors.readers.ReaderStarsByRestaurant.Response.GetStarsByRestaurantResponse
import com.vgomez.app.http.validators._
import com.vgomez.app.http.RouterUtility._

import scala.util.{Failure, Success}

// Restaurant Router.
class RestaurantRouter(administration: ActorRef)(implicit system: ActorSystem, implicit val timeout: Timeout)
  extends RestaurantCreationRequestJsonProtocol with RestaurantUpdateRequestJsonProtocol
    with RestaurantResponseJsonProtocol with StarsResponseJsonProtocol with FailureResponseJsonProtocol
    with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher

  def getRestaurant(id: String): Future[GetRestaurantResponse] =
    (administration ? GetRestaurant(id)).mapTo[GetRestaurantResponse]

  /*
  Todo #1
    Description: Decouple restaurant.
    Action: Create new method to return stars by restaurant Id.
    Status: Done
    Reported by: Sebastian Oliveri.
  */
  def getStarsByRestaurant(id: String): Future[GetStarsByRestaurantResponse] =
    (administration ? GetStarsByRestaurant(id)).mapTo[GetStarsByRestaurantResponse]

  def registerRestaurant(restaurantCreationRequest: RestaurantCreationRequest): Future[RegisterResponse] =
    (administration ? restaurantCreationRequest.toCommand).mapTo[RegisterResponse]

  def updateRestaurant(id: String,
                       restaurantUpdateRequest: RestaurantUpdateRequest): Future[UpdateResponse] =
    (administration ? restaurantUpdateRequest.toCommand(id)).mapTo[UpdateResponse]

  def unregisterRestaurant(id: String): Future[UnregisterResponse] =
    (administration ? UnregisterRestaurant(id)).mapTo[UnregisterResponse]

  def getAllRestaurant(pageNumber: Long, numberOfElementPerPage: Long): Future[GetAllRestaurantResponse] =
    (administration ? GetAllRestaurant(pageNumber, numberOfElementPerPage)).mapTo[GetAllRestaurantResponse]


  val routes: Route =
    pathPrefix("api" / "restaurants"){
      path(Segment) { id =>
        get {
          parameter('service.as[String]) {
            case "get-state" =>
              onSuccess(getRestaurant(id)) {
                /*
                Todo #2
                  Description: Decouple restaurant endpoint.
                  Action: Remove start from this class.
                  Status: Done
                  Reported by: Sebastian Oliveri.
                */
                case GetRestaurantResponse(Some(restaurantState)) =>
                  complete {
                    getRestaurantResponseByRestaurantState(restaurantState)
                  }

                case GetRestaurantResponse(None) =>
                  complete(StatusCodes.NotFound, FailureResponse(s"Restaurant $id cannot be found"))
              }

            case "get-stars" =>
              onSuccess(getStarsByRestaurant(id)) {
                case GetStarsByRestaurantResponse(Some(stars)) =>
                  complete {
                    StarsResponse(stars)
                  }

                case GetStarsByRestaurantResponse(None) =>
                  complete(StatusCodes.NotFound, FailureResponse(s"There are not reviews for the restaurant " +
                    s"with id $id"))
              }

            case _ =>
              complete(StatusCodes.BadRequest, FailureResponse(s"The specify service parameter hasn't been found!"))
          }

        } ~
          put {
            entity(as[RestaurantUpdateRequest]) { request =>
              ValidatorRestaurantRequest(request.username, request.name, request.state, request.city, request.postalCode,
                                          request.latitude, request.longitude, request.categories,
                                            request.schedule).run() match {
                case Success(_) =>
                  onSuccess(updateRestaurant(id, request)) {
                    case UpdateResponse(Success(Done)) =>
                      respondWithHeader(Location(s"/restaurants/$id")) {
                        complete(StatusCodes.OK)
                      }
                    case UpdateResponse(Failure(e: RuntimeException)) =>
                      complete(StatusCodes.BadRequest, FailureResponse(e.getMessage))
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          } ~
          delete {
            onSuccess(unregisterRestaurant(id)) {
              case UnregisterResponse(Success(_)) =>
                complete(StatusCodes.NoContent)
              case UnregisterResponse(Failure(_)) =>
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
                onSuccess(registerRestaurant(request)) {
                  case RegisterResponse(Success(id)) =>
                    respondWithHeader(Location(s"/restaurants/$id")) {
                      complete(StatusCodes.Created)
                    }
                  case RegisterResponse(Failure(e: RuntimeException)) =>
                    complete(StatusCodes.BadRequest, FailureResponse(e.getMessage))
                }
              case Failure(e: ValidationFailException) =>
                complete(StatusCodes.BadRequest, FailureResponse(e.message))
            }
          }
        }~
          get {
            parameter('pageNumber.as[Long], 'numberOfElementPerPage.as[Long]) { (pageNumber: Long,
                                                                                 numberOfElementPerPage: Long) =>
              ValidatorRequestWithPagination(pageNumber, numberOfElementPerPage).run() match {
                case Success(_) =>
                  onSuccess(getAllRestaurant(pageNumber, numberOfElementPerPage)) {
                    case GetAllRestaurantResponse(Some(getRestaurantResponses)) => complete {
                      getRestaurantResponses.map(getRestaurantResponseByGetRestaurantResponse)
                    }

                    case GetAllRestaurantResponse(None) =>
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

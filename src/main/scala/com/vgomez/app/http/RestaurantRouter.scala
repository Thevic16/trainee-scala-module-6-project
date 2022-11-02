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
import com.vgomez.app.http.messages.HttpRequest._
import com.vgomez.app.http.messages.HttpResponse._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.actors.Administration.Command.{GetAllRestaurant, GetStarsByRestaurant}
import com.vgomez.app.actors.Restaurant.RestaurantState
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.http.validators._
import com.vgomez.app.http.RouterUtility._

import scala.util.{Failure, Success, Try}

// Restaurant Router.
class RestaurantRouter(administration: ActorRef)(implicit system: ActorSystem, implicit val timeout: Timeout)
  extends RestaurantCreationRequestJsonProtocol with RestaurantUpdateRequestJsonProtocol
    with RestaurantResponseJsonProtocol with StarsResponseJsonProtocol with FailureResponseJsonProtocol
    with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher

  def getRestaurant(id: String): Future[Option[RestaurantState]] =
    (administration ? GetRestaurant(id)).mapTo[Option[RestaurantState]]

  /*
  Todo #2
    Description: Decouple restaurant endpoint.
    Action: Create new method to return stars by restaurant Id.
    Status: Done
    Reported by: Sebastian Oliveri.
  */
  def getStarsByRestaurant(id: String): Future[Option[Int]] =
    (administration ? GetStarsByRestaurant(id)).mapTo[Option[Int]]

  def registerRestaurant(restaurantCreationRequest: RestaurantCreationRequest): Future[Try[String]] =
    (administration ? restaurantCreationRequest.toCommand).mapTo[Try[String]]

  def updateRestaurant(id: String,
                       restaurantUpdateRequest: RestaurantUpdateRequest): Future[Try[Done]] =
    (administration ? restaurantUpdateRequest.toCommand(id)).mapTo[Try[Done]]

  def unregisterRestaurant(id: String): Future[Try[Done]] =
    (administration ? UnregisterRestaurant(id)).mapTo[Try[Done]]

  def getAllRestaurant(pageNumber: Long, numberOfElementPerPage: Long): Future[Option[List[RestaurantState]]] =
    (administration ? GetAllRestaurant(pageNumber, numberOfElementPerPage)).mapTo[Option[List[RestaurantState]]]


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
                case Some(restaurantState) =>
                  complete {
                    getRestaurantResponseByRestaurantState(restaurantState)
                  }

                case None =>
                  complete(StatusCodes.NotFound, FailureResponse(s"Restaurant $id cannot be found"))
              }

            case "get-stars" =>
              onSuccess(getStarsByRestaurant(id)) {
                case Some(stars) =>
                  complete {
                    StarsResponse(stars)
                  }

                case None =>
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
                    case Success(Done) =>
                      respondWithHeader(Location(s"/restaurants/$id")) {
                        complete(StatusCodes.OK)
                      }
                    case Failure(e: RuntimeException) =>
                      complete(StatusCodes.BadRequest, FailureResponse(e.getMessage))
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          } ~
          delete {
            onSuccess(unregisterRestaurant(id)) {
              case Success(_) =>
                complete(StatusCodes.NoContent)
              case Failure(_) =>
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
                  case Success(id) =>
                    respondWithHeader(Location(s"/restaurants/$id")) {
                      complete(StatusCodes.Created)
                    }
                  case Failure(e: RuntimeException) =>
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

                    case Some(listRestaurantState) => complete {
                      listRestaurantState.map(getRestaurantResponseByRestaurantState)
                    }
                    case None =>
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

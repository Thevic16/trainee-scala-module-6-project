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
import com.vgomez.app.http.HttpResponse._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.exception.CustomException.IdentifierNotFoundException

import scala.util.{Failure, Success}

// Resquest clases
case class RestaurantCreationRequest(userId: String , name: String, state: String, city: String, postalCode: String,
                                     latitude: Double, longitude: Double, categories: Set[String],
                                     schedule: SimpleScheduler) {

  def toCommand: CreateRestaurant = CreateRestaurant(None, RestaurantInfo(userId, name, state, city, postalCode,
    DomainModel.Location(latitude, longitude), categories: Set[String], transformSimpleSchedulerToSchedule(schedule)))
}

trait RestaurantCreationRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val restaurantCreationRequestJson = jsonFormat9(RestaurantCreationRequest)
}

case class RestaurantUpdateRequest(userId: String , name: String, state: String, city: String, postalCode: String,
                                   latitude: Double, longitude: Double, categories: Set[String],
                                   schedule: SimpleScheduler) {
  def toCommand(id: String): UpdateRestaurant = UpdateRestaurant(id, RestaurantInfo(userId, name, state, city, postalCode,
    DomainModel.Location(latitude, longitude), categories: Set[String], transformSimpleSchedulerToSchedule(schedule)))
}

trait RestaurantUpdateRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val restaurantUpdateRequestJson = jsonFormat9(RestaurantUpdateRequest)
}

// Response class
case class RestaurantResponse(userId: String , name: String, state: String, city: String, postalCode: String,
                              latitude: Double, longitude: Double, categories: Set[String],
                              schedule: SimpleScheduler, starts: Int)

trait RestaurantResponseJsonProtocol extends DefaultJsonProtocol {
  implicit val restaurantResponseJson = jsonFormat10(RestaurantResponse)
}


// Restaurant Router.
class RestaurantRouter(administration: ActorRef)(implicit system: ActorSystem)
  extends RestaurantCreationRequestJsonProtocol with RestaurantUpdateRequestJsonProtocol
    with RestaurantResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  def getRestaurant(id: String): Future[GetRestaurantResponse] =
    (administration ? GetRestaurant(id)).mapTo[GetRestaurantResponse]

  def createRestaurant(restaurantCreationRequest: RestaurantCreationRequest): Future[CreateRestaurantResponse] =
    (administration ? restaurantCreationRequest.toCommand).mapTo[CreateRestaurantResponse]

  def updateRestaurant(id: String,
                       restaurantUpdateRequest: RestaurantUpdateRequest): Future[UpdateRestaurantResponse] =
    (administration ? restaurantUpdateRequest.toCommand(id)).mapTo[UpdateRestaurantResponse]

  def deleteRestaurant(id: String): Future[DeleteRestaurantResponse] =
    (administration ? DeleteRestaurant(id)).mapTo[DeleteRestaurantResponse]

  val routes: Route =
    pathPrefix("api" / "restaurants"){
      path(Segment) { id =>
        get {
          onSuccess(getRestaurant(id)) {
            case GetRestaurantResponse(Some(restaurantState), Some(starts)) =>
              complete {
                RestaurantResponse(restaurantState.userId, restaurantState.name, restaurantState.state,
                  restaurantState.city, restaurantState.postalCode, restaurantState.location.latitude,
                  restaurantState.location.longitude, restaurantState.categories,
                  transformScheduleToSimpleScheduler(restaurantState.schedule), starts)
              }

            case _ =>
              complete(StatusCodes.NotFound, FailureResponse(s"Restaurant $id cannot be found"))
          }
        } ~
          put {
            entity(as[RestaurantUpdateRequest]) { request =>
              onSuccess(updateRestaurant(id, request)) {
                case UpdateRestaurantResponse(Success(_)) =>
                  respondWithHeader(Location(s"/restaurants/$id")) {
                    complete(StatusCodes.OK)
                  }
                case UpdateRestaurantResponse(Failure(IdentifierNotFoundException)) =>
                  complete(StatusCodes.NotFound, FailureResponse(s"Restaurant $id cannot be found"))
              }
            }
          } ~
          delete {
            onSuccess(deleteRestaurant(id)) {
              case DeleteRestaurantResponse(Success(_)) =>
                complete(StatusCodes.NoContent)
              case DeleteRestaurantResponse(Failure(IdentifierNotFoundException)) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Restaurant $id cannot be found"))
            }
          }
      }~
      pathEndOrSingleSlash {
        post {
          entity(as[RestaurantCreationRequest]){ request =>
            onSuccess(createRestaurant(request)){
              case CreateRestaurantResponse(id) =>
                respondWithHeader(Location(s"/restaurants/$id")){
                  complete(StatusCodes.Created)
                }
            }
          }
        }
      }
    }
}

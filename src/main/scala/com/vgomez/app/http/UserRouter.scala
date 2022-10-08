package com.vgomez.app.http
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask

import scala.concurrent.duration._
import akka.util.Timeout
import akka.http.scaladsl.model.headers.Location

import scala.concurrent.{ExecutionContext, Future, duration}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.vgomez.app.actors.User._
import com.vgomez.app.actors.User.Command._
import com.vgomez.app.actors.User.Response._
import com.vgomez.app.http.messages.HttpRequest._
import com.vgomez.app.http.messages.HttpResponse._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.actors.Administration.Command.GetAllUser
import com.vgomez.app.domain.DomainModel
import com.vgomez.app.domain.Transformer.{transformRoleToStringRole, transformStringRoleToRole}
import com.vgomez.app.exception.CustomException.{IdentifierNotFoundException, ValidationFailException}
import com.vgomez.app.actors.abtractions.Abstract.Response._
import com.vgomez.app.actors.readers.ReaderGetAll.Response.GetAllUserResponse
import com.vgomez.app.http.validators._


import scala.util.{Failure, Success}

// User Router.
class UserRouter(administration: ActorRef)(implicit system: ActorSystem)
  extends UserCreationRequestJsonProtocol with UserUpdateRequestJsonProtocol
    with UserResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.seconds)

  def getUser(username: String): Future[GetUserResponse] =
    (administration ? GetUser(username)).mapTo[GetUserResponse]

  def createUser(userCreationRequest: UserCreationRequest): Future[CreateResponse] =
    (administration ? userCreationRequest.toCommand).mapTo[CreateResponse]

  def updateUser(userUpdateRequest: UserUpdateRequest): Future[UpdateUserResponse] =
    (administration ? userUpdateRequest.toCommand).mapTo[UpdateUserResponse]

  def deleteUser(username: String): Future[DeleteResponse] =
    (administration ? DeleteUser(username)).mapTo[DeleteResponse]

  def getAllUser(pageNumber: Long): Future[GetAllUserResponse] =
    (administration ? GetAllUser(pageNumber)).mapTo[GetAllUserResponse]

  val routes: Route =
    pathPrefix("api" / "users"){
      path(Segment) { username =>
        get {
          onSuccess(getUser(username)) {
            case GetUserResponse(Some(userState)) =>
              complete {
                UserResponse(userState.username,userState.password, transformRoleToStringRole(userState.role),
                  userState.location.latitude,  userState.location.longitude,userState.favoriteCategories)
              }
            case GetUserResponse(None) =>
              complete(StatusCodes.NotFound, FailureResponse(s"User $username cannot be found"))
          }
        } ~
          put {
            entity(as[UserUpdateRequest]) { request =>
              ValidatorUserRequest(request.username, request.password, request.role, request.latitude, request.longitude,
                request.favoriteCategories).run() match {
                case Success(_) =>
                  onSuccess(updateUser(request)) {
                    case UpdateUserResponse(Success(_)) =>
                      respondWithHeader(Location(s"/users/$username")) {
                        complete(StatusCodes.OK)
                      }
                    case UpdateUserResponse(Failure(_)) =>
                      complete(StatusCodes.NotFound, FailureResponse(s"User $username cannot be found"))
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          } ~
          delete {
            onSuccess(deleteUser(username)) {
              case DeleteResponse(Success(_)) =>
                complete(StatusCodes.NoContent)
              case DeleteResponse(Failure(_)) =>
                complete(StatusCodes.NotFound, FailureResponse(s"User $username cannot be found"))
            }
          }
      }~
        pathEndOrSingleSlash {
          post {
            entity(as[UserCreationRequest]){ request =>
              ValidatorUserRequest(request.username, request.password, request.role, request.latitude, request.longitude,
                request.favoriteCategories).run() match {
                case Success(_) =>
                  onSuccess(createUser(request)) {
                    case CreateResponse(id) =>
                      respondWithHeader(Location(s"/users/$id")) {
                        complete(StatusCodes.Created)
                      }
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          }
        }
    }~
      get {
        parameter('pageNumber.as[Long]) { (pageNumber: Long) =>
          onSuccess(getAllUser(pageNumber)) {
            case GetAllUserResponse(Some(getUserResponses)) => complete {
              getUserResponses.map(getUserResponseByGetUserResponse)
            }

            case GetAllUserResponse(None) =>
              complete(StatusCodes.NotFound, FailureResponse(s"There are not element in this pageNumber."))
          }

        }
      }

  def getUserResponseByGetUserResponse(getUserResponse: GetUserResponse): UserResponse = {
    getUserResponse match {
      case GetUserResponse(Some(userState)) =>
        UserResponse(userState.username, userState.password, transformRoleToStringRole(userState.role),
          userState.location.latitude, userState.location.longitude, userState.favoriteCategories)
    }
  }

}

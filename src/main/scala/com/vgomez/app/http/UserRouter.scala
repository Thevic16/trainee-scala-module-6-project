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
import com.vgomez.app.actors.User.Command._
import com.vgomez.app.actors.User.Response._
import com.vgomez.app.http.messages.HttpRequest._
import com.vgomez.app.http.messages.HttpResponse._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.actors.Administration.Command.GetAllUser
import com.vgomez.app.actors.User.{RegisterUserState, UnregisterUserState}
import com.vgomez.app.domain.Transformer.transformRoleToStringRole
import com.vgomez.app.exception.CustomException.ValidationFailException
import com.vgomez.app.actors.messages.AbstractMessage.Response._
import com.vgomez.app.actors.readers.ReaderGetAll.Response.GetAllUserResponse
import com.vgomez.app.http.validators._
import com.vgomez.app.http.RouterUtility._

import scala.util.{Failure, Success}

// User Router.
class UserRouter(administration: ActorRef)(implicit system: ActorSystem, implicit val timeout: Timeout)
  extends UserCreationRequestJsonProtocol with UserUpdateRequestJsonProtocol
    with UserResponseJsonProtocol with FailureResponseJsonProtocol with SprayJsonSupport{

  implicit val dispatcher: ExecutionContext = system.dispatcher

  def getUser(username: String): Future[GetUserResponse] =
    (administration ? GetUser(username)).mapTo[GetUserResponse]

  def createUser(userCreationRequest: UserCreationRequest): Future[CreateResponse] =
    (administration ? userCreationRequest.toCommand).mapTo[CreateResponse]

  def updateUser(userUpdateRequest: UserUpdateRequest): Future[UpdateResponse] =
    (administration ? userUpdateRequest.toCommand).mapTo[UpdateResponse]

  def deleteUser(username: String): Future[DeleteResponse] =
    (administration ? DeleteUser(username)).mapTo[DeleteResponse]

  def getAllUser(pageNumber: Long, numberOfElementPerPage: Long): Future[GetAllUserResponse] =
    (administration ? GetAllUser(pageNumber, numberOfElementPerPage)).mapTo[GetAllUserResponse]

  val routes: Route =
    pathPrefix("api" / "users"){
      path(Segment) { username =>
        get {
          onSuccess(getUser(username)) {
            case GetUserResponse(Some(userState)) =>
              userState match {
                case RegisterUserState(username, _, password, role, location, favoriteCategories) =>
                  complete {
                    UserResponse(username, password, transformRoleToStringRole(role),
                      location.latitude, location.longitude, favoriteCategories)
                  }
                case UnregisterUserState =>
                  complete(StatusCodes.NotFound, FailureResponse(s"User $username cannot be found"))
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
                    case UpdateResponse(Success(Done)) =>
                      respondWithHeader(Location(s"/users/$username")) {
                        complete(StatusCodes.OK)
                      }
                    case UpdateResponse(Failure(_)) =>
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
                    case CreateResponse(Success(id)) =>
                      respondWithHeader(Location(s"/users/$id")) {
                        complete(StatusCodes.Created)
                      }
                    case CreateResponse(Failure(e: RuntimeException)) =>
                      complete(StatusCodes.BadRequest, FailureResponse(e.getMessage))
                  }
                case Failure(e: ValidationFailException) =>
                  complete(StatusCodes.BadRequest, FailureResponse(e.message))
              }
            }
          } ~
            get {
              parameter('pageNumber.as[Long], 'numberOfElementPerPage.as[Long]) { (pageNumber: Long,
                                                                                   numberOfElementPerPage: Long) =>
                ValidatorRequestWithPagination(pageNumber, numberOfElementPerPage).run() match {
                  case Success(_) =>
                    onSuccess(getAllUser(pageNumber, numberOfElementPerPage)) {
                      case GetAllUserResponse(Some(getUserResponses)) => complete {
                        getUserResponses.map(getUserResponseByGetUserResponse)
                      }

                      case GetAllUserResponse(None) =>
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

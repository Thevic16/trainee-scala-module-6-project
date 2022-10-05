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
import com.vgomez.app.http.messages.HttpResponse._
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.vgomez.app.domain.DomainModel
import com.vgomez.app.domain.Transformer.{transformRoleToStringRole, transformStringRoleToRole}
import com.vgomez.app.exception.CustomException.{IdentifierNotFoundException, ValidationFailException}
import com.vgomez.app.actors.commands.Abstract.Response._
import com.vgomez.app.http.validators._

import scala.util.{Failure, Success}

// Resquest clases
case class UserCreationRequest(username: String, password: String, role: String, latitude: Double, longitude: Double,
                               favoriteCategories: Set[String]) {
  def toCommand: CreateUser = CreateUser(UserInfo(username, password, transformStringRoleToRole(role),
    DomainModel.Location(latitude, longitude), favoriteCategories))
}

trait UserCreationRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val userCreationRequestJson = jsonFormat6(UserCreationRequest)
}

case class UserUpdateRequest(username: String, password: String, role: String, latitude: Double, longitude: Double,
                             favoriteCategories: Set[String]) {
  def toCommand: UpdateUser = UpdateUser(UserInfo(username, password, transformStringRoleToRole(role),
    DomainModel.Location(latitude, longitude), favoriteCategories))
}

trait UserUpdateRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val userUpdateRequestJson = jsonFormat6(UserUpdateRequest)
}

// Response class
case class UserResponse(username: String, password: String, role: String, latitude: Double, longitude: Double,
                        favoriteCategories: Set[String])

trait UserResponseJsonProtocol extends DefaultJsonProtocol {
  implicit val userResponseJson = jsonFormat6(UserResponse)
}


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
            case _ =>
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
                    case UpdateUserResponse(Failure(IdentifierNotFoundException)) =>
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
              case DeleteResponse(Failure(IdentifierNotFoundException)) =>
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
    }
}

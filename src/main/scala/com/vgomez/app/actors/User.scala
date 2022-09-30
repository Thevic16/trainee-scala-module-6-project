package com.vgomez.app.actors
import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor

import scala.util.{Success, Try}
import com.vgomez.app.domain.DomainModel._

object User {

  // state
  case class UserInfo(username: String, password: String, role: Role, location: Location,
                       favoriteCategories: Set[String])

  case class UserState(username: String, password: String, role: Role, location: Location,
                       favoriteCategories: Set[String], isDeleted: Boolean)

  // commands
  object Command {
    case class GetUser(username: String)
    case class CreateUser(userInfo: UserInfo)
    case class UpdateUser(userInfo: UserInfo)
    case class DeleteUser(username: String)
  }

  // events
  case class UserCreated(UserState: UserState)
  case class UserUpdated(UserState: UserState)
  case class UserDeleted(UserState: UserState)


  // responses
  object Response {
    case class GetUserResponse(maybeUserState: Option[UserState])

    case class CreateUserResponse(maybeUsername: Try[String])

    case class UpdateUserResponse(maybeUserState: Try[UserState])

    case class DeleteUserResponse(maybeUsername: Try[String])
  }

  def props(username: String): Props =  Props(new User(username))

}

class User(username: String) extends PersistentActor with ActorLogging{
  import User._
  import Command._
  import Response._

  override def persistenceId: String = username

  def state(userState: UserState): Receive = {
    case GetUser(_) =>
      log.info(s"User with username $username receive a GetUser Command")
      sender() ! GetUserResponse(Some(userState))

    case CreateUser(userInfo) =>
      log.info(s"User with username $username receive a CreateUser Command")
      val newState: UserState = getNewState(userInfo)

      persist(UserCreated(newState)) { _ =>
        sender() ! CreateUserResponse(Success(username))
        context.become(state(newState))
      }

    case UpdateUser(userInfo) =>
      val newState: UserState = getNewState(userInfo)

      persist(UserUpdated(newState)) { _ =>
        sender() ! UpdateUserResponse(Success(newState))
        context.become(state(newState))
      }

    case DeleteUser(id) =>
      val newState: UserState = userState.copy(isDeleted = true)

      persist(UserDeleted(newState)) { _ =>
        sender() ! DeleteUserResponse(Success(id))
        context.become(state(newState))
      }
  }

  override def receiveCommand: Receive = state(getState())

  override def receiveRecover: Receive = {
    case UserCreated(userState) =>
      context.become(state(userState))

    case UserUpdated(userState) =>
      context.become(state(userState))

    case UserDeleted(userState) =>
      context.become(state(userState))
  }

  def getState(username: String = username, password: String = "", role: Role = Normal,
               location: Location = Location(0,0), favoriteCategories: Set[String] = Set()): UserState = {
      UserState(username, password, role, location, favoriteCategories, false)
  }

  def getNewState(userInfo: UserInfo): UserState = {
    getState(userInfo.username, userInfo.password, userInfo.role, userInfo.location, userInfo.favoriteCategories)
  }
}

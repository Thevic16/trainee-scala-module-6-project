package com.vgomez.app.actors
import akka.Done
import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor

import scala.util.{Failure, Success}
import com.vgomez.app.domain.DomainModel._
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Response._
import com.vgomez.app.exception.CustomException.EntityIsDeletedException


object User {

  // state
  case class UserInfo(username: String, password: String, role: Role, location: Location,
                       favoriteCategories: Set[String])

  case class UserState(username: String, index: Long, password: String, role: Role, location: Location,
                       favoriteCategories: Set[String], isDeleted: Boolean)

  // commands
  object Command {
    case class GetUser(username: String) extends GetCommand
    case class CreateUser(userInfo: UserInfo) extends CreateCommand
    case class UpdateUser(userInfo: UserInfo) extends UpdateCommand
    case class DeleteUser(username: String) extends DeleteCommand
  }

  // events
  case class UserCreated(UserState: UserState)
  case class UserUpdated(UserState: UserState)
  case class UserDeleted(UserState: UserState)


  // responses
  object Response {
    case class GetUserResponse(maybeUserState: Option[UserState]) extends GetResponse
  }

  def props(username: String, index: Long): Props =  Props(new User(username, index))

}

class User(username: String, index: Long) extends PersistentActor with ActorLogging{
  import User._
  import Command._
  import Response._

  override def persistenceId: String = username

  def state(userState: UserState): Receive = {
    case GetUser(_) =>
      if(userState.isDeleted){
        sender() ! GetUserResponse(None)
      }
      else
        sender() ! GetUserResponse(Some(userState))

    case CreateUser(userInfo) =>
      val newState: UserState = getNewState(userInfo)

      persist(UserCreated(newState)) { _ =>
        sender() ! CreateResponse(Success(username))
        context.become(state(newState))
      }

    case UpdateUser(userInfo) =>
      if(userState.isDeleted)
        sender() ! UpdateResponse(Failure(EntityIsDeletedException))
      else {
        val newState: UserState = getNewState(userInfo)

        persist(UserUpdated(newState)) { _ =>
          sender() ! UpdateResponse(Success(Done))
          context.become(state(newState))
        }
      }

    case DeleteUser(id) =>
      if(userState.isDeleted)
        sender() ! DeleteResponse(Failure(EntityIsDeletedException))
      else {
        val newState: UserState = userState.copy(isDeleted = true)

        persist(UserDeleted(newState)) { _ =>
          sender() ! DeleteResponse(Success(Done))
          context.become(state(newState))
        }
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
      UserState(username, index, password, role, location, favoriteCategories, isDeleted = false)
  }

  def getNewState(userInfo: UserInfo): UserState = {
    getState(userInfo.username, userInfo.password, userInfo.role, userInfo.location, userInfo.favoriteCategories)
  }
}

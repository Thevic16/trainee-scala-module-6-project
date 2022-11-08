
// Copyright (C) 2022 Víctor Gómez.

package com.vgomez.app.actors

import akka.Done
import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Event.EventUser
import com.vgomez.app.domain.DomainModel._
import com.vgomez.app.exception.CustomException.UserUnRegisteredException

import scala.util.{Failure, Success}


object User {
  def props(username: String, index: Long): Props = Props(new User(username, index))

  // state
  sealed abstract class UserState

  case class UserInfo(username: String, password: String, role: Role, location: Location,
    favoriteCategories: Set[String])

  case class RegisterUserState(username: String, index: Long, password: String, role: Role,
    location: Location, favoriteCategories: Set[String]) extends UserState

  // events
  case class UserRegistered(UserState: UserState) extends EventUser

  case class UserUpdated(UserState: UserState) extends EventUser

  case class UserUnregistered(username: String, UserState: UserState) extends EventUser

  case object UnregisterUserState extends UserState

  // commands
  object Command {
    case class GetUser(username: String) extends GetCommand

    case class RegisterUser(userInfo: UserInfo) extends RegisterCommand

    case class UpdateUser(userInfo: UserInfo) extends UpdateCommand

    case class UnregisterUser(username: String) extends UnregisterCommand
  }

}

class User(username: String, index: Long) extends PersistentActor with ActorLogging {

  import User._
  import Command._

  override def persistenceId: String = username

  def state(userState: UserState): Receive = {
    case GetUser(_) =>
      userState match {
        case RegisterUserState(_, _, _, _, _, _) =>
          sender() ! Some(userState)
        case UnregisterUserState =>
          sender() ! None
      }

    case RegisterUser(userInfo) =>
      val newState: UserState = getNewState(userInfo)

      persist(UserRegistered(newState)) { _ =>
        sender() ! Success(username)
        context.become(state(newState))
      }

    case UpdateUser(userInfo) =>
      userState match {
        case RegisterUserState(_, _, _, _, _, _) =>
          val newState: UserState = getNewState(userInfo)

          persist(UserUpdated(newState)) { _ =>
            sender() ! Success(Done)
            context.become(state(newState))
          }
        case UnregisterUserState =>
          sender() ! Failure(UserUnRegisteredException)
      }

    case UnregisterUser(_) =>
      userState match {
        case RegisterUserState(_, _, _, _, _, _) =>
          val newState: UserState = UnregisterUserState

          persist(UserUnregistered(username, newState)) { _ =>
            sender() ! Success(Done)
            context.become(state(newState))
          }
        case UnregisterUserState =>
          sender() ! Failure(UserUnRegisteredException)
      }
  }

  override def receiveCommand: Receive = state(getState())

  override def receiveRecover: Receive = {
    case UserRegistered(userState) =>
      context.become(state(userState))

    case UserUpdated(userState) =>
      context.become(state(userState))

    case UserUnregistered(_, userState) =>
      context.become(state(userState))
  }

  def getState(username: String = username, password: String = "", role: Role = Normal,
    location: Location = Location(0, 0), favoriteCategories: Set[String] = Set()): UserState = {
    RegisterUserState(username, index, password, role, location, favoriteCategories)
  }

  def getNewState(userInfo: UserInfo): UserState = {
    getState(userInfo.username, userInfo.password, userInfo.role, userInfo.location,
      userInfo.favoriteCategories)
  }
}

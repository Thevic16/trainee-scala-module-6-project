package com.vgomez.app.actors.intermediate

import akka.actor.{Actor, ActorRef, Stash}
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.{RegisterUserState, UnregisterUserState}




object IntermediateReadUserAttributes {
  // commands
  object Command {
    case class GetUserFavoriteCategories(username: String)
    case class GetUserLocation(username: String)
  }

  object ChooseAttribute {
    trait UserAttribute
    case object UserFavoriteCategories extends UserAttribute
    case object UserLocation extends UserAttribute
  }

}

class IntermediateReadUserAttributes extends Actor with Stash{
  import IntermediateReadUserAttributes.Command._
  import IntermediateReadUserAttributes.ChooseAttribute._

  override def receive: Receive = {
    case GetUserFavoriteCategories(username) =>
      context.parent ! GetUser(username)
      unstashAll()
      context.become(receiveUserState(sender(), UserFavoriteCategories))

    case GetUserLocation(username) =>
      context.parent ! GetUser(username)
      unstashAll()
      context.become(receiveUserState(sender(), UserLocation))

    case _ =>
      stash()
  }

  def receiveUserState(originalSender: ActorRef, userAttribute: UserAttribute): Receive = {
    case Some(userState) =>
      userState match {
        case RegisterUserState(_, _, _, _, location, favoriteCategories) =>
          userAttribute match {
            case UserFavoriteCategories =>
              originalSender ! Some(favoriteCategories)
            case UserLocation =>
              originalSender ! Some(location)
          }

        case UnregisterUserState =>
          originalSender ! None
      }

      unstashAll()
      context.become(receive)

    case None =>
      originalSender ! None

      unstashAll()
      context.become(receive)

    case _ =>
      stash()
  }
}

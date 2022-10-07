package com.vgomez.app.actors

import scala.util.Failure
import akka.actor.ActorRef
import com.vgomez.app.actors.Administration.AdministrationState
import com.vgomez.app.actors.Restaurant.Command._
import com.vgomez.app.actors.Restaurant.Response._
import com.vgomez.app.actors.Review.Command._
import com.vgomez.app.actors.Review.Response._
import com.vgomez.app.actors.User.Command._
import com.vgomez.app.actors.User.Response._
import com.vgomez.app.actors.abtractions.Abstract.Command._
import com.vgomez.app.actors.abtractions.Abstract.Response._
import com.vgomez.app.exception.CustomException._

import java.util.UUID

object AdministrationUtility {

  def getActorRefOptionByGetCommand(getCommand: GetCommand,
                                    administrationState: AdministrationState): Option[ActorRef] = {
    getCommand match {
      case GetRestaurant(id) => administrationState.restaurants.get(id)
      case GetReview(id) => administrationState.reviews.get(id)
      case GetUser(username) => administrationState.users.get(username)
    }
  }

  def getGetResponseByGetCommand(getCommand: GetCommand): GetResponse = {
    getCommand match {
      case GetRestaurant(_) => GetRestaurantResponse(None, None)
      case GetReview(_) => GetReviewResponse(None)
      case GetUser(_) => GetUserResponse(None)
    }
  }

  def getIdentifierByCreateCommand(createCommand: CreateCommand): String = {
    val identifierOption: Option[String] = createCommand match {
      case CreateRestaurant(maybeId, _) => maybeId
      case CreateReview(maybeId, _) => maybeId
      case CreateUser(userInfo) => Some(userInfo.username)
    }
    identifierOption.getOrElse(UUID.randomUUID().toString)
  }

  def getActorRefOptionByCreateCommand(createCommand: CreateCommand, identifier: String,
                                       administrationState: AdministrationState): Option[ActorRef] = {
    createCommand match {
      case CreateRestaurant(_, _) => administrationState.restaurants.get(identifier)
      case CreateReview(_, _) => administrationState.reviews.get(identifier)
      case CreateUser(_) => administrationState.users.get(identifier)
    }
  }

  def getNewStateByCreateCommand(createCommand: CreateCommand, newActorRef: ActorRef, identifier: String,
                                 administrationState: AdministrationState): AdministrationState = {
    createCommand match {
      case CreateRestaurant(_, _) => administrationState.copy(
        restaurants = administrationState.restaurants + (identifier -> newActorRef))

      case CreateReview(_, _) => administrationState.copy(
        reviews = administrationState.reviews + (identifier -> newActorRef))

      case CreateUser(_) => administrationState.copy(
        users = administrationState.users + (identifier -> newActorRef))
    }
  }

  def getIdentifierByUpdateCommand(updateCommand: UpdateCommand): String = {
    updateCommand match {
      case UpdateRestaurant(id, _) => id
      case UpdateReview(id, _) => id
      case UpdateUser(userInfo) => userInfo.username
    }
  }

  def getActorRefOptionByUpdateCommand(updateCommand: UpdateCommand, identifier: String,
                                       administrationState: AdministrationState): Option[ActorRef] = {
    updateCommand match {
      case UpdateRestaurant(_, _) => administrationState.restaurants.get(identifier)
      case UpdateReview(_, _) => administrationState.reviews.get(identifier)
      case UpdateUser(_) => administrationState.users.get(identifier)
    }
  }

  def getUpdateResponseByUpdateCommand(updateCommand: UpdateCommand): UpdateResponse = {
    updateCommand match {
      case UpdateRestaurant(_, _) => UpdateRestaurantResponse(Failure(IdentifierNotFoundException))
      case UpdateReview(_, _) => UpdateReviewResponse(Failure(IdentifierNotFoundException))
      case UpdateUser(_) => UpdateUserResponse(Failure(IdentifierNotFoundException))
    }
  }

  def getActorRefOptionByDeleteCommand(deleteCommand: DeleteCommand,
                                       administrationState: AdministrationState): Option[ActorRef] = {
    deleteCommand match {
      case DeleteRestaurant(id) => administrationState.restaurants.get(id)
      case DeleteReview(id) => administrationState.reviews.get(id)
      case DeleteUser(username) => administrationState.users.get(username)
    }
  }

}

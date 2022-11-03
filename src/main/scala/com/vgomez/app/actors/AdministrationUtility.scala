package com.vgomez.app.actors

import scala.util.{Failure, Success, Try}
import akka.actor.{ActorContext, ActorRef}
import com.vgomez.app.actors.Administration.AdministrationState
import com.vgomez.app.actors.Restaurant.Command._
import com.vgomez.app.actors.Review.Command._
import com.vgomez.app.actors.User.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.exception.CustomException._

import java.util.UUID

object AdministrationUtility {

  // Get Commands relate
  def getActorRefOptionByGetCommand(getCommand: GetCommand,
                                    administrationState: AdministrationState): Option[(Long, ActorRef)] = {
    getCommand match {
      case GetRestaurant(id) => administrationState.restaurants.get(id)
      case GetReview(id) => administrationState.reviews.get(id)
      case GetUser(username) => administrationState.users.get(username)
    }
  }

  // Register Command related.
  def getIdentifierByRegisterCommand(registerCommand: RegisterCommand): String = {
    val identifierOption: Option[String] = registerCommand match {
      case RegisterRestaurant(maybeId, _) => maybeId
      case RegisterReview(maybeId, _) => maybeId
      case RegisterUser(userInfo) => Some(userInfo.username)
    }
    identifierOption.getOrElse(UUID.randomUUID().toString)
  }

  def getActorRefOptionByRegisterCommand(registerCommand: RegisterCommand, identifier: String,
                                       administrationState: AdministrationState): Option[(Long, ActorRef)] = {
    registerCommand match {
      case RegisterRestaurant(_, _) => administrationState.restaurants.get(identifier)
      case RegisterReview(_, _) => administrationState.reviews.get(identifier)
      case RegisterUser(_) => administrationState.users.get(identifier)
    }
  }

  def getNewStateByRegisterCommand(registerCommand: RegisterCommand, newActorRef: ActorRef, identifier: String,
                                 administrationState: AdministrationState): AdministrationState = {
    registerCommand match {
      case RegisterRestaurant(_, _) => administrationState.copy(
        restaurants = administrationState.restaurants + (identifier -> (administrationState.currentRestaurantIndex,
                                                                        newActorRef)),
        currentRestaurantIndex = administrationState.currentRestaurantIndex + 1)

      case RegisterReview(_, _) => administrationState.copy(
        reviews = administrationState.reviews + (identifier -> (administrationState.currentReviewIndex, newActorRef)),
        currentReviewIndex = administrationState.currentReviewIndex + 1)

      case RegisterUser(_) => administrationState.copy(
        users = administrationState.users + (identifier -> (administrationState.currentUserIndex, newActorRef)),
        currentUserIndex = administrationState.currentUserIndex + 1)
    }
  }

  def getNewActorRefByRegisterCommand(context: ActorContext, administrationState: AdministrationState,
                                    registerCommand: RegisterCommand, identifier: String): ActorRef = {
    registerCommand match {
      case RegisterRestaurant(_, _) => context.actorOf(Restaurant.props(identifier,
                                                      administrationState.currentRestaurantIndex), identifier)

      case RegisterReview(_, _) => context.actorOf(Review.props(identifier,
                                                                    administrationState.currentReviewIndex), identifier)

      case RegisterUser(_) => context.actorOf(User.props(identifier, administrationState.currentUserIndex), identifier)
    }
  }

  def getRegisterResponseExistsExceptionByRegisterCommand(registerCommand: RegisterCommand): Failure[Nothing] = {
    registerCommand match {
      case RegisterRestaurant(_, _) => Failure(RestaurantExistsException())
      case RegisterReview(_, _) => Failure(ReviewExistsException())
      case RegisterUser(_) => Failure(UserExistsException())
    }
  }

  // Update Commands Related

  def getIdentifierByUpdateCommand(updateCommand: UpdateCommand): String = {
    updateCommand match {
      case UpdateRestaurant(id, _) => id
      case UpdateReview(id, _) => id
      case UpdateUser(userInfo) => userInfo.username
    }
  }

  def getActorRefOptionByUpdateCommand(updateCommand: UpdateCommand, identifier: String,
                                       administrationState: AdministrationState): Option[(Long, ActorRef)] = {
    updateCommand match {
      case UpdateRestaurant(_, _) => administrationState.restaurants.get(identifier)
      case UpdateReview(_, _) => administrationState.reviews.get(identifier)
      case UpdateUser(_) => administrationState.users.get(identifier)
    }
  }

  def getUpdateResponseNotFoundExceptionByUpdateCommand(updateCommand: UpdateCommand): Failure[Nothing] = {
    updateCommand match {
      case UpdateRestaurant(_, _) => Failure(RestaurantNotFoundException())
      case UpdateReview(_, _) => Failure(ReviewNotFoundException())
      case UpdateUser(_) => Failure(UserNotFoundException())
    }
  }

  def getUpdateResponseNotFoundExceptionByUpdateCommandWithMessage(updateCommand: UpdateCommand,
                                                         message: String): Failure[Nothing] = {
    updateCommand match {
      case UpdateRestaurant(_, _) => Failure(RestaurantNotFoundException(message))
      case UpdateReview(_, _) => Failure(ReviewNotFoundException(message))
      case UpdateUser(_) => Failure(UserNotFoundException(message))
    }
  }


  // Unregister Command related.
  def getActorRefOptionByUnregisterCommand(unregisterCommand: UnregisterCommand,
                                       administrationState: AdministrationState): Option[(Long, ActorRef)] = {
    unregisterCommand match {
      case UnregisterRestaurant(id) => administrationState.restaurants.get(id)
      case UnregisterReview(id) => administrationState.reviews.get(id)
      case UnregisterUser(username) => administrationState.users.get(username)
    }
  }

  def getUnregisterResponseNotFoundExceptionByUnregisterCommand(unregisterCommand: UnregisterCommand): Failure[Nothing] = {
    unregisterCommand match {
      case UnregisterRestaurant(_) => Failure(RestaurantNotFoundException())
      case UnregisterReview(_) => Failure(ReviewNotFoundException())
      case UnregisterUser(_) => Failure(UserNotFoundException())
    }
  }


  // Verify Ids query commands related
  def verifyIdsOnRegisterCommand(registerCommand: RegisterCommand,
                               administrationState: AdministrationState): Try[RegisterCommand] = {
    registerCommand match {
      case RegisterRestaurant(_, restaurantInfo) =>
        if(usernameExist(restaurantInfo.username, administrationState))
          Success(registerCommand)
        else
          Failure(UserNotFoundException())

      case RegisterReview(_, reviewInfo) =>
        if (!usernameExist(reviewInfo.username, administrationState))
          Failure(UserNotFoundException())
        else if (!restaurantExist(reviewInfo.restaurantId, administrationState))
          Failure(RestaurantNotFoundException())
        else
          Success(registerCommand)

      case RegisterUser(_) => Success(registerCommand)
    }
  }

  def usernameExist(username: String, administrationState: AdministrationState): Boolean = {
    administrationState.users.get(username) match {
      case Some(_) => true
      case None => false
    }
  }

  def restaurantExist(restaurant: String, administrationState: AdministrationState): Boolean = {
    administrationState.restaurants.get(restaurant) match {
      case Some(_) => true
      case None => false
    }
  }

  def verifyIdsOnUpdateCommand(updateCommand: UpdateCommand,
                               administrationState: AdministrationState): Try[UpdateCommand] = {
    updateCommand match {
      case UpdateRestaurant(_, restaurantInfo) =>
        if (usernameExist(restaurantInfo.username, administrationState))
          Success(updateCommand)
        else
          Failure(UserNotFoundException())

      case UpdateReview(_, reviewInfo) =>
        if(!usernameExist(reviewInfo.username, administrationState))
          Failure(UserNotFoundException())
        else if(!restaurantExist(reviewInfo.restaurantId, administrationState))
          Failure(RestaurantNotFoundException())
        else
          Success(updateCommand)

      case UpdateUser(_) => Success(updateCommand)
    }
  }

}

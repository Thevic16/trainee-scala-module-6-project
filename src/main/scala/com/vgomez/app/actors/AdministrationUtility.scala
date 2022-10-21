package com.vgomez.app.actors

import scala.util.{Failure, Success, Try}
import akka.actor.{ActorContext, ActorRef}
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

  // Get Commands relate
  def getActorRefOptionByGetCommand(getCommand: GetCommand,
                                    administrationState: AdministrationState): Option[(Int, ActorRef)] = {
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

  // Create Command related.
  def getIdentifierByCreateCommand(createCommand: CreateCommand): String = {
    val identifierOption: Option[String] = createCommand match {
      case CreateRestaurant(maybeId, _) => maybeId
      case CreateReview(maybeId, _) => maybeId
      case CreateUser(userInfo) => Some(userInfo.username)
    }
    identifierOption.getOrElse(UUID.randomUUID().toString)
  }

  def getActorRefOptionByCreateCommand(createCommand: CreateCommand, identifier: String,
                                       administrationState: AdministrationState): Option[(Int, ActorRef)] = {
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
        restaurants = administrationState.restaurants + (identifier -> (administrationState.currentRestaurantIndex, newActorRef)),
        currentRestaurantIndex = administrationState.currentRestaurantIndex + 1)

      case CreateReview(_, _) => administrationState.copy(
        reviews = administrationState.reviews + (identifier -> (administrationState.currentReviewIndex, newActorRef)),
        currentReviewIndex = administrationState.currentReviewIndex + 1)

      case CreateUser(_) => administrationState.copy(
        users = administrationState.users + (identifier -> (administrationState.currentUserIndex, newActorRef)),
        currentUserIndex = administrationState.currentUserIndex + 1)
    }
  }

  def getNewActorRefByCreateCommand(context: ActorContext, administrationState: AdministrationState,
                                    createCommand: CreateCommand, identifier: String): ActorRef = {
    createCommand match {
      case CreateRestaurant(_, _) => context.actorOf(Restaurant.props(identifier,
                                                      administrationState.currentRestaurantIndex), identifier)

      case CreateReview(_, _) => context.actorOf(Review.props(identifier,
                                                                    administrationState.currentReviewIndex), identifier)

      case CreateUser(_) => context.actorOf(User.props(identifier, administrationState.currentUserIndex), identifier)
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
                                       administrationState: AdministrationState): Option[(Int, ActorRef)] = {
    updateCommand match {
      case UpdateRestaurant(_, _) => administrationState.restaurants.get(identifier)
      case UpdateReview(_, _) => administrationState.reviews.get(identifier)
      case UpdateUser(_) => administrationState.users.get(identifier)
    }
  }

  def getUpdateResponseFailureByUpdateCommand(updateCommand: UpdateCommand): UpdateResponse = {
    updateCommand match {
      case UpdateRestaurant(_, _) => UpdateRestaurantResponse(Failure(IdentifierNotFoundException()))
      case UpdateReview(_, _) => UpdateReviewResponse(Failure(IdentifierNotFoundException()))
      case UpdateUser(_) => UpdateUserResponse(Failure(IdentifierNotFoundException()))
    }
  }

  def getUpdateResponseFailureByUpdateCommandWithMessage(updateCommand: UpdateCommand, message: String): UpdateResponse = {
    updateCommand match {
      case UpdateRestaurant(_, _) => UpdateRestaurantResponse(Failure(IdentifierNotFoundException(message)))
      case UpdateReview(_, _) => UpdateReviewResponse(Failure(IdentifierNotFoundException(message)))
      case UpdateUser(_) => UpdateUserResponse(Failure(IdentifierNotFoundException(message)))
    }
  }


  // Delete Command related.
  def getActorRefOptionByDeleteCommand(deleteCommand: DeleteCommand,
                                       administrationState: AdministrationState): Option[(Int, ActorRef)] = {
    deleteCommand match {
      case DeleteRestaurant(id) => administrationState.restaurants.get(id)
      case DeleteReview(id) => administrationState.reviews.get(id)
      case DeleteUser(username) => administrationState.users.get(username)
    }
  }


  // Verify Ids query commands related
  def verifyIdsOnCreateCommand(createCommand: CreateCommand,
                               administrationState: AdministrationState): Try[CreateCommand] = {
    createCommand match {
      case CreateRestaurant(_, restaurantInfo) =>
        if(usernameExist(restaurantInfo.username, administrationState))
          Success(createCommand)
        else
          Failure(IdentifierNotFoundException("username identifier field is no found."))

      case CreateReview(_, reviewInfo) =>
        if (usernameExist(reviewInfo.username, administrationState) &&
            restaurantExist(reviewInfo.restaurantId, administrationState))
          Success(createCommand)
        else
          Failure(IdentifierNotFoundException("Username/RestaurantId identifier field is no found."))

      case CreateUser(_) => Success(createCommand)
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
          Failure(IdentifierNotFoundException("Username identifier is no found."))

      case UpdateReview(_, reviewInfo) =>
        if (usernameExist(reviewInfo.username, administrationState) &&
          restaurantExist(reviewInfo.restaurantId, administrationState))
          Success(updateCommand)
        else
          Failure(IdentifierNotFoundException("Username/RestaurantId identifier is no found."))

      case UpdateUser(_) => Success(updateCommand)
    }
  }

}

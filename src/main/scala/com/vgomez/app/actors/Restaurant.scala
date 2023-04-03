
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.actors

import akka.Done
import akka.actor.Props
import akka.persistence.PersistentActor
import com.vgomez.app.actors.Restaurant.Command._
import com.vgomez.app.actors.Restaurant._
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Event.EventRestaurant
import com.vgomez.app.domain.DomainModel._
import com.vgomez.app.exception.CustomException.RestaurantUnRegisteredException

import scala.util.{Failure, Success}

object Restaurant {
  def props(id: String, index: Long): Props = Props(new Restaurant(id, index))

  // state
  sealed trait RestaurantState

  case class RestaurantInfo(username: String, name: String, state: String, city: String, postalCode: String,
    location: Location, categories: Set[String], timetable: Timetable)

  case class RegisterRestaurantState(id: String, index: Long, username: String, name: String, state: String,
    city: String, postalCode: String, location: Location,
    categories: Set[String], timetable: Timetable)
    extends RestaurantState

  // events
  case class RestaurantRegistered(restaurantState: RestaurantState) extends EventRestaurant

  case class RestaurantUpdated(restaurantState: RestaurantState) extends EventRestaurant

  case class RestaurantUnregistered(id: String, restaurantState: RestaurantState) extends EventRestaurant

  case object UnregisterRestaurantState extends RestaurantState

  // commands
  object Command {
    case class GetRestaurant(id: String) extends GetCommand

    case class RegisterRestaurant(maybeId: Option[String], restaurantInfo: RestaurantInfo)
      extends RegisterCommand

    case class UpdateRestaurant(id: String, restaurantInfo: RestaurantInfo) extends UpdateCommand

    case class UnregisterRestaurant(id: String) extends UnregisterCommand
  }

}

class Restaurant(id: String, index: Long) extends PersistentActor {

  override def persistenceId: String = id

  def state(restaurantState: RestaurantState): Receive = {
    case GetRestaurant(_) =>
      restaurantState match {
        case restaurantState@RegisterRestaurantState(_, _, _, _, _, _, _, _, _, _) =>
          sender() ! Some(restaurantState)
        case UnregisterRestaurantState =>
          sender() ! None
      }

    case RegisterRestaurant(_, restaurantInfo) =>
      val newState: RestaurantState = getNewState(restaurantInfo)

      persist(RestaurantRegistered(newState)) { _ =>
        sender() ! Success(id)
        context.become(state(newState))
      }

    case UpdateRestaurant(_, restaurantInfo) =>
      restaurantState match {
        case RegisterRestaurantState(_, _, _, _, _, _, _, _, _, _) =>
          val newState: RestaurantState = getNewState(restaurantInfo)

          persist(RestaurantUpdated(newState)) { _ =>
            sender() ! Success(Done)
            context.become(state(newState))
          }

        case UnregisterRestaurantState =>
          sender() ! Failure(RestaurantUnRegisteredException)
      }

    case UnregisterRestaurant(_) =>
      restaurantState match {
        case RegisterRestaurantState(_, _, _, _, _, _, _, _, _, _) =>
          val newState: RestaurantState = UnregisterRestaurantState

          persist(RestaurantUnregistered(id, newState)) { _ =>
            sender() ! Success(Done)
            context.become(state(newState))
          }

        case UnregisterRestaurantState =>
          sender() ! Failure(RestaurantUnRegisteredException)
      }
  }

  override def receiveCommand: Receive = state(getState())

  override def receiveRecover: Receive = {
    case RestaurantRegistered(restaurantState) =>
      context.become(state(restaurantState))

    case RestaurantUpdated(restaurantState) =>
      context.become(state(restaurantState))

    case RestaurantUnregistered(_, restaurantState) =>
      context.become(state(restaurantState))
  }

  def getState(username: String = "", name: String = "", state: String = "",
    city: String = "", postalCode: String = "", location: Location = Location(latitude = 0, longitude = 0),
    categories: Set[String] = Set(),
    timetable: Timetable = UnavailableTimetable): RestaurantState = {
    RegisterRestaurantState(id, index, username, name, state, city, postalCode, location, categories,
      timetable)
  }

  def getNewState(restaurantInfo: RestaurantInfo): RestaurantState = {
    getState(restaurantInfo.username, restaurantInfo.name, restaurantInfo.state,
      restaurantInfo.city, restaurantInfo.postalCode, restaurantInfo.location,
      restaurantInfo.categories, restaurantInfo.timetable)
  }

}

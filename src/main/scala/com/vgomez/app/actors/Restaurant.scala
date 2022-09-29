package com.vgomez.app.actors
import akka.actor.Props
import akka.persistence.PersistentActor

import scala.util.{Success, Try}
import com.vgomez.app.domain.DomainModel._
import com.vgomez.app.domain.DomainModelFactory.{generateNewEmptySchedule, updateSchedule}

object Restaurant {

  // state
  case class RestaurantInfo(userId: String , name: String, state: String, city: String, postalCode: String,
                            location: Location, categories: Set[String], schedule: Schedule)

  case class RestaurantState(id: String, userId: String,  name: String, state: String, city: String, postalCode: String,
                             location: Location, categories: Set[String], schedule: Schedule,
                             isDeleted: Boolean)

  // commands
  object Command {
    case class GetRestaurant(id: String)
    case class CreateRestaurant(restaurantInfo: RestaurantInfo)
    case class UpdateRestaurant(id: String, restaurantInfo: RestaurantInfo)
    case class DeleteRestaurant(id: String)
  }

  // events
  case class RestaurantCreated(restaurantState: RestaurantState)
  case class RestaurantUpdated(restaurantState: RestaurantState)
  case class RestaurantDeleted(restaurantState: RestaurantState)


  // responses
  object Response {
    case class GetRestaurantResponse(maybeRestaurantState: Option[RestaurantState], maybeStarts: Option[Int])

    case class CreateRestaurantResponse(id: String)

    case class UpdateRestaurantResponse(maybeRestaurantState: Try[RestaurantState])

    case class DeleteRestaurantResponse(maybeId: Try[String])
  }

  def props(id: String): Props =  Props(new Restaurant(id))

}

class Restaurant(id: String) extends PersistentActor{
  import Restaurant._
  import Command._
  import Response._

  override def persistenceId: String = id

  def state(restaurantState: RestaurantState): Receive = {
    case GetRestaurant(_) =>
      /*
      * Todo implement starts in GetRestaurantResponse
      **/
      sender() ! GetRestaurantResponse(Some(restaurantState), Some(5))

    case CreateRestaurant(restaurantInfo) =>
      val newState: RestaurantState = getNewState(restaurantInfo)

      persist(RestaurantCreated(newState)){_ =>
        sender() ! CreateRestaurantResponse(id)
        context.become(state(newState))
      }

    case UpdateRestaurant(_, restaurantInfo) =>
      val newState: RestaurantState = getNewState(restaurantInfo)

      persist(RestaurantUpdated(newState)) { _ =>
        sender() ! UpdateRestaurantResponse(Success(newState))
        context.become(state(newState))
      }

    case DeleteRestaurant(id) =>
      val newState: RestaurantState = restaurantState.copy(isDeleted = true)

      persist(RestaurantDeleted(newState)) { _ =>
        sender() ! DeleteRestaurantResponse(Success(id))
        context.become(state(newState))
      }
  }

  override def receiveCommand: Receive = state(getState())

  override def receiveRecover: Receive = {
    case RestaurantCreated(restaurantState) =>
      context.become(state(restaurantState))

    case RestaurantUpdated(restaurantState) =>
      context.become(state(restaurantState))

    case RestaurantDeleted(restaurantState) =>
      context.become(state(restaurantState))
  }

  def getState(userId: String = "", name:String = "", state: String = "",
               city: String = "", postalCode: String = "", location: Location = Location(0, 0),
               categories: Set[String] = Set(), schedule: Schedule = generateNewEmptySchedule()): RestaurantState = {
    RestaurantState(id, userId, name, state, city, postalCode, location, categories, schedule, false)
  }

  def getNewState(restaurantInfo: RestaurantInfo): RestaurantState = {
    getState(restaurantInfo.userId, restaurantInfo.name, restaurantInfo.state,
      restaurantInfo.city, restaurantInfo.postalCode, restaurantInfo.location,
      restaurantInfo.categories, restaurantInfo.schedule)
  }

}

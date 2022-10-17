package com.vgomez.app.actors
import akka.actor.{ActorRef, Props, Stash}
import akka.persistence.PersistentActor
import com.vgomez.app.actors.Administration.Command.GetStarsByRestaurant

import scala.util.{Failure, Success, Try}
import com.vgomez.app.domain.DomainModel._
import com.vgomez.app.domain.DomainModelFactory.generateNewEmptySchedule
import com.vgomez.app.actors.abtractions.Abstract.Command._
import com.vgomez.app.actors.abtractions.Abstract.Response._
import com.vgomez.app.actors.readers.ReaderStarsByRestaurant.Response.GetStarsByRestaurantResponse
import com.vgomez.app.exception.CustomException.EntityIsDeletedException


object Restaurant {

  // state
  case class RestaurantInfo(username: String , name: String, state: String, city: String, postalCode: String,
                            location: Location, categories: Set[String], schedule: Schedule)

  case class RestaurantState(id: String, username: String,  name: String, state: String, city: String, postalCode: String,
                             location: Location, categories: Set[String], schedule: Schedule,
                             isDeleted: Boolean)

  // commands
  object Command {
    case class GetRestaurant(id: String) extends GetCommand
    case class CreateRestaurant(maybeId: Option[String], restaurantInfo: RestaurantInfo) extends CreateCommand
    case class UpdateRestaurant(id: String, restaurantInfo: RestaurantInfo) extends UpdateCommand
    case class DeleteRestaurant(id: String) extends DeleteCommand
  }

  // events
  case class RestaurantCreated(restaurantState: RestaurantState)
  case class RestaurantUpdated(restaurantState: RestaurantState)
  case class RestaurantDeleted(restaurantState: RestaurantState)


  // responses
  object Response {
    case class GetRestaurantResponse(maybeRestaurantState: Option[RestaurantState], maybeStars: Option[Int])
      extends GetResponse

    case class UpdateRestaurantResponse(maybeRestaurantState: Try[RestaurantState]) extends UpdateResponse
  }

  def props(id: String): Props =  Props(new Restaurant(id))

}

class Restaurant(id: String) extends PersistentActor with Stash{
  import Restaurant._
  import Command._
  import Response._

  override def persistenceId: String = id

  def state(restaurantState: RestaurantState): Receive = {
    case GetRestaurant(_) =>
      if (restaurantState.isDeleted)
        sender() ! GetRestaurantResponse(None, None)
      else {
        context.parent ! GetStarsByRestaurant(id)
        unstashAll()
        context.become(getStarsState(restaurantState, sender()))
      }

    case CreateRestaurant(_, restaurantInfo) =>
      val newState: RestaurantState = getNewState(restaurantInfo)

      persist(RestaurantCreated(newState)){_ =>
        sender() ! CreateResponse(Success(id))
        context.become(state(newState))
      }

    case UpdateRestaurant(_, restaurantInfo) =>
      if(restaurantState.isDeleted)
        sender() ! UpdateRestaurantResponse(Failure(EntityIsDeletedException))
      else{
        val newState: RestaurantState = getNewState(restaurantInfo)

        persist(RestaurantUpdated(newState)) { _ =>
          sender() ! UpdateRestaurantResponse(Success(newState))
          context.become(state(newState))
        }
      }

    case DeleteRestaurant(id) =>
      if (restaurantState.isDeleted)
        sender() ! DeleteResponse(Failure(EntityIsDeletedException))
      else {
        val newState: RestaurantState = restaurantState.copy(isDeleted = true)

        persist(RestaurantDeleted(newState)) { _ =>
          sender() ! DeleteResponse(Success(id))
          context.become(state(newState))
        }
      }

    case _ =>
      stash()
  }

  def getStarsState(restaurantState: RestaurantState, originalSender: ActorRef): Receive = {
    case GetStarsByRestaurantResponse(starts) =>
      originalSender ! GetRestaurantResponse(Some(restaurantState), Some(starts))

      unstashAll()
      context.become(state(restaurantState))

    case _ =>
      stash()
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

  def getState(username: String = "", name:String = "", state: String = "",
               city: String = "", postalCode: String = "", location: Location = Location(0, 0),
               categories: Set[String] = Set(), schedule: Schedule = generateNewEmptySchedule()): RestaurantState = {
    RestaurantState(id, username, name, state, city, postalCode, location, categories, schedule, false)
  }

  def getNewState(restaurantInfo: RestaurantInfo): RestaurantState = {
    getState(restaurantInfo.username, restaurantInfo.name, restaurantInfo.state,
      restaurantInfo.city, restaurantInfo.postalCode, restaurantInfo.location,
      restaurantInfo.categories, restaurantInfo.schedule)
  }

}

package com.vgomez.app.actors
import akka.Done
import akka.actor.{ActorRef, Props, Stash}
import akka.persistence.PersistentActor
import com.vgomez.app.actors.Administration.Command.GetStarsByRestaurant

import scala.util.{Failure, Success}
import com.vgomez.app.domain.DomainModel._
import com.vgomez.app.domain.DomainModelFactory.generateNewEmptySchedule
import com.vgomez.app.actors.messages.AbstractMessage.Command._
import com.vgomez.app.actors.messages.AbstractMessage.Response._
import com.vgomez.app.actors.readers.ReaderStarsByRestaurant.Response.GetStarsByRestaurantResponse
import com.vgomez.app.exception.CustomException.EntityIsDeletedException


object Restaurant {
  case class RestaurantInfo(username: String, name: String, state: String, city: String, postalCode: String,
                            location: Location, categories: Set[String], schedule: Schedule)

  // state
  sealed abstract class RestaurantState

  case class RegisterRestaurantState(id: String, index: Long,  username: String,  name: String, state: String, city: String,
                             postalCode: String, location: Location, categories: Set[String], schedule: Schedule)
                              extends RestaurantState

  case object UnregisterRestaurantState extends RestaurantState

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
  }

  def props(id: String, index: Long): Props =  Props(new Restaurant(id, index))

}

class Restaurant(id: String, index: Long) extends PersistentActor with Stash{
  import Restaurant._
  import Command._
  import Response._

  override def persistenceId: String = id

  def state(restaurantState: RestaurantState): Receive = {
    case GetRestaurant(_) =>
      restaurantState match {
        case restaurantState@RegisterRestaurantState(_, _, _, _, _, _, _, _, _, _) =>
          context.parent ! GetStarsByRestaurant(id)
          unstashAll()
          context.become(getStarsState(restaurantState, sender()))
        case UnregisterRestaurantState =>
          sender() ! GetRestaurantResponse(None, None)
      }

    case CreateRestaurant(_, restaurantInfo) =>
      val newState: RestaurantState = getNewState(restaurantInfo)

      persist(RestaurantCreated(newState)){_ =>
        sender() ! CreateResponse(Success(id))
        context.become(state(newState))
      }

    case UpdateRestaurant(_, restaurantInfo) =>
      restaurantState match {
        case RegisterRestaurantState(_, _, _, _, _, _, _, _, _, _) =>
          val newState: RestaurantState = getNewState(restaurantInfo)

          persist(RestaurantUpdated(newState)) { _ =>
            sender() ! UpdateResponse(Success(Done))
            context.become(state(newState))
          }

        case UnregisterRestaurantState =>
          sender() ! UpdateResponse(Failure(EntityIsDeletedException))
      }

    case DeleteRestaurant(_) =>
      restaurantState match {
        case RegisterRestaurantState(_, _, _, _, _, _, _, _, _, _) =>
          val newState: RestaurantState = UnregisterRestaurantState

          persist(RestaurantDeleted(newState)) { _ =>
            sender() ! DeleteResponse(Success(Done))
            context.become(state(newState))
          }

        case UnregisterRestaurantState =>
          sender() ! DeleteResponse(Failure(EntityIsDeletedException))
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
    RegisterRestaurantState(id, index, username, name, state, city, postalCode, location, categories, schedule)
  }

  def getNewState(restaurantInfo: RestaurantInfo): RestaurantState = {
    getState(restaurantInfo.username, restaurantInfo.name, restaurantInfo.state,
      restaurantInfo.city, restaurantInfo.postalCode, restaurantInfo.location,
      restaurantInfo.categories, restaurantInfo.schedule)
  }

}

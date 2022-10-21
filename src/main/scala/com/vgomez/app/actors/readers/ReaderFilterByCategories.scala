package com.vgomez.app.actors.readers

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import akka.persistence.PersistentActor
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.{Flow, RunnableGraph, Source}
import com.vgomez.app.actors.Restaurant.Command.GetRestaurant
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.User.Command.GetUser
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.abtractions.Abstract.Event.Event
import com.vgomez.app.actors.abtractions.Abstract.Response.GetRecommendationResponse
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.Response._
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.getMaterializeGraphReaderUtility
import com.vgomez.app.actors.readers.ReaderUtility.getListRestaurantResponsesBySeqRestaurantModels
import com.vgomez.app.domain.DomainModelOperation.restaurantCategoriesIsContainsByQueryCategories
import com.vgomez.app.data.database.Operation
import com.vgomez.app.data.database.Model

import scala.concurrent.Future

object ReaderFilterByCategories {
  // state
  case class ReaderFilterByCategoriesState(restaurants: Set[String])

  // commands
  object Command {
    case class CreateRestaurant(id: String, categories: Set[String])
    case class UpdateRestaurant(id: String, categories: Set[String])

    // Recommendations Categories
    case class GetRecommendationFilterByFavoriteCategories(favoriteCategories: Set[String], pageNumber: Long,
                                                           numberOfElementPerPage: Long)
    case class GetRecommendationFilterByUserFavoriteCategories(username: String, pageNumber: Long,
                                                               numberOfElementPerPage: Long)
  }

  // events
  case class RestaurantCreated(id: String, categories: Set[String]) extends Event
  case class RestaurantUpdated(id: String, categories: Set[String]) extends Event

  def props(system: ActorSystem): Props =  Props(new ReaderFilterByCategories(system))

  // WriteEventAdapter
  class ReaderFilterByCategoriesEventAdapter extends WriteEventAdapter {
    override def toJournal(event: Any): Any = event match {
      case RestaurantCreated(_, categories) =>
        Tagged(event, categories)
      case RestaurantUpdated(_, categories) =>
        Tagged(event, categories)
      case _ =>
        event
    }
    override def manifest(event: Any): String = ""
  }

}

class ReaderFilterByCategories(system: ActorSystem) extends PersistentActor with ActorLogging with Stash {
  import ReaderFilterByCategories._
  import Command._
  import system.dispatcher

  // ReaderDatabaseUtility
  val readerDatabaseUtility = ReaderDatabaseUtility(system)

  // state
  var readerFilterByCategoriesRecoveryState = ReaderFilterByCategoriesState(Set())

  override def persistenceId: String = "reader-filter-by-categories"

  def state(readerFilterByCategoriesState: ReaderFilterByCategoriesState): Receive = {
    case CreateRestaurant(id, categories) =>
      val newState: ReaderFilterByCategoriesState = readerFilterByCategoriesState.copy(
                                                          restaurants = readerFilterByCategoriesState.restaurants + id)
      persist(RestaurantCreated(id, categories)) { _ =>
        log.info(s"ReaderFilterByCategories create a restaurant with id: $id")
        context.become(state(newState))
      }

    case UpdateRestaurant(id, categories) =>
      persist(RestaurantUpdated(id, categories)) { _ =>
        log.info(s"ReaderFilterByCategories update a restaurant with id: $id")
      }

    case GetRecommendationFilterByFavoriteCategories(favoriteCategories, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByFavoriteCategories command.")
      Operation.getRestaurantsModelByCategories(favoriteCategories.toList, pageNumber, numberOfElementPerPage).pipeTo(self)
      unstashAll()
      context.become(getAllRestaurantState(readerFilterByCategoriesState, sender()))

    case GetRecommendationFilterByUserFavoriteCategories(username, pageNumber, numberOfElementPerPage) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByUserFavoriteCategories command.")
      context.parent ! GetUser(username)
      unstashAll()
      context.become(halfwayGetRecommendationFilterByUserFavoriteCategories(readerFilterByCategoriesRecoveryState,
                                                                            sender(), pageNumber, numberOfElementPerPage))

    case _ =>
      stash()
  }
  
  def getAllRestaurantState(readerFilterByCategoriesState: ReaderFilterByCategoriesState, originalSender: ActorRef,
                            restaurantModels: Seq[Model.RestaurantModel] = Seq()): Receive = {

    case restaurantModels: Seq[Model.RestaurantModel] =>
      val seqRestaurantId = restaurantModels.map(model => model.id)
      Operation.getReviewsStarsByListRestaurantId(seqRestaurantId).pipeTo(self)
      context.become(getAllRestaurantState(readerFilterByCategoriesState, originalSender, restaurantModels))

    case reviewsStars: Seq[Seq[Int]] =>
      originalSender ! GetRecommendationResponse(Some(getListRestaurantResponsesBySeqRestaurantModels(restaurantModels,
                                                            reviewsStars)))
      unstashAll()
      context.become(state(readerFilterByCategoriesState))

    case _ =>
      stash()
  }


  def halfwayGetRecommendationFilterByUserFavoriteCategories(readerFilterByCategoriesState: ReaderFilterByCategoriesState,
                                                             originalSender: ActorRef, pageNumber: Long,
                                                             numberOfElementPerPage: Long): Receive = {
    case GetUserResponse(Some(userState)) =>
      Operation.getRestaurantsModelByCategories(userState.favoriteCategories.toList, pageNumber,
        numberOfElementPerPage).pipeTo(self)

      unstashAll()
      context.become(getAllRestaurantState(readerFilterByCategoriesState, originalSender))

    case GetUserResponse(None) =>
      originalSender ! GetRecommendationResponse(None)
      unstashAll()
      context.become(state(readerFilterByCategoriesRecoveryState))

    case _ =>
      stash()
  }

  override def receiveCommand: Receive = state(ReaderFilterByCategoriesState(Set()))

  override def receiveRecover: Receive = {
    case RestaurantCreated(id, _) =>
      log.info(s"ReaderFilterByCategories has recovered a restaurant with id: $id")
      readerFilterByCategoriesRecoveryState = readerFilterByCategoriesRecoveryState.copy(
        restaurants = readerFilterByCategoriesRecoveryState.restaurants + id)

      context.become(state(readerFilterByCategoriesRecoveryState))

    case RestaurantUpdated(id, _) => log.info(s"ReaderFilterByCategories has recovered a update " +
                                              s"for restaurant with id: $id")
  }

}

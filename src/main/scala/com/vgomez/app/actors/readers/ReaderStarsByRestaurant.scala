package com.vgomez.app.actors.readers

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.data.projectionDatabase.Operation
import com.vgomez.app.data.projectionDatabase.Response.GetReviewModelsStarsResponse

object ReaderStarsByRestaurant {
  // commands
  object Command {
    case class GetStarsByRestaurant(restaurantId: String)
  }

  object Response {
    case class GetStarsByRestaurantResponse(optionStars: Option[Int])
  }

  def props(system: ActorSystem): Props =  Props(new ReaderStarsByRestaurant(system))
}

class ReaderStarsByRestaurant(system: ActorSystem) extends Actor with ActorLogging with Stash {

  import ReaderStarsByRestaurant._
  import Command._
  import Response._
  import system.dispatcher

  def state(): Receive = {
    case GetStarsByRestaurant(restaurantId) =>
      log.info("ReaderFilterByCategories has receive a GetRecommendationFilterByFavoriteCategories command.")
      Operation.getReviewsStarsByRestaurantId(restaurantId).mapTo[GetReviewModelsStarsResponse].pipeTo(self)
      unstashAll()
      context.become(getStartByRestaurantState(sender()))

    case _ =>
      stash()
  }
  
  def getStartByRestaurantState(originalSender: ActorRef): Receive = {

    case GetReviewModelsStarsResponse(reviewModelsStars) =>
      if(reviewModelsStars.nonEmpty)
        originalSender ! GetStarsByRestaurantResponse(Some(reviewModelsStars.sum / reviewModelsStars.length))
      else
        originalSender ! GetStarsByRestaurantResponse(None)

      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}

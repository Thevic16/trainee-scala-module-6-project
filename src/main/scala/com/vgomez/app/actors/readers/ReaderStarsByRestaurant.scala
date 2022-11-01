package com.vgomez.app.actors.readers

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.data.indexDatabase.Operation
import com.vgomez.app.data.indexDatabase.Response.GetReviewModelsStarsResponse

/*
Todo #R
  Description: Remove responses classes from actors.
  Action: Remove response class from ReaderStarsByRestaurant Actor.
  Status: Done
  Reported by: Sebastian Oliveri.
*/
object ReaderStarsByRestaurant {
  // commands
  object Command {
    case class GetStarsByRestaurant(restaurantId: String)
  }

  def props(system: ActorSystem): Props =  Props(new ReaderStarsByRestaurant(system))
}

class ReaderStarsByRestaurant(system: ActorSystem) extends Actor with ActorLogging with Stash {

  import ReaderStarsByRestaurant._
  import Command._
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
        originalSender ! Some(reviewModelsStars.sum / reviewModelsStars.length)
      else
        originalSender ! None

      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}

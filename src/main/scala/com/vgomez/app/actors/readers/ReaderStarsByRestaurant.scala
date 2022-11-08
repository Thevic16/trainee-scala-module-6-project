
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.actors.readers

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.readers.ReaderStarsByRestaurant.Command._
import com.vgomez.app.data.projection.Operation
import com.vgomez.app.data.projection.Response.GetReviewModelsStarsResponse

object ReaderStarsByRestaurant {
  def props(system: ActorSystem): Props = Props(new ReaderStarsByRestaurant(system))

  // commands
  object Command {
    case class GetStarsByRestaurant(restaurantId: String)
  }
}

class ReaderStarsByRestaurant(system: ActorSystem) extends Actor with ActorLogging with Stash {

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
      if (reviewModelsStars.nonEmpty) {
        originalSender ! Some(reviewModelsStars.sum / reviewModelsStars.length)
      }
      else {
        originalSender ! None
      }

      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}

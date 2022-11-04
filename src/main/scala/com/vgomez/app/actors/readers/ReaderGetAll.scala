
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.actors.readers


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.readers.ReaderUtility._
import com.vgomez.app.data.projectionDatabase.Response.{GetRestaurantModelsResponse, GetReviewModelsResponse,
                                                    GetUserModelsResponse}
import com.vgomez.app.data.projectionDatabase.Operation


object ReaderGetAll {
  // commands
  object Command {
    case class GetAllRestaurant(pageNumber: Long, numberOfElementPerPage: Long)
    case class GetAllReview(pageNumber: Long, numberOfElementPerPage: Long)
    case class GetAllUser(pageNumber: Long, numberOfElementPerPage: Long)
  }

  def props(system: ActorSystem): Props =  Props(new ReaderGetAll(system))

}

class ReaderGetAll(system: ActorSystem) extends Actor with ActorLogging with Stash {
  import ReaderGetAll._
  import Command._
  import system.dispatcher


  def state(): Receive = {
    case GetAllRestaurant(pageNumber, numberOfElementPerPage) =>
      log.info("ReaderGetAll has receive a GetAllRestaurant command.")
      Operation.getAllRestaurantModel(pageNumber, numberOfElementPerPage).mapTo[GetRestaurantModelsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllRestaurantState(sender()))

    case GetAllReview(pageNumber, numberOfElementPerPage) =>
      log.info("ReaderGetAll has receive a GetAllReview command.")
      Operation.getAllReviewModel(pageNumber, numberOfElementPerPage).mapTo[GetReviewModelsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllReviewState(sender()))

    case GetAllUser(pageNumber, numberOfElementPerPage) =>
      log.info("ReaderGetAll has receive a GetAllUser command.")
      Operation.getAllUserModel(pageNumber, numberOfElementPerPage).mapTo[GetUserModelsResponse].pipeTo(self)
      unstashAll()
      context.become(getAllUserState(sender()))

    case _ =>
      stash()
  }
  
  def getAllRestaurantState(originalSender: ActorRef): Receive = {
    case GetRestaurantModelsResponse(restaurantModels) =>
      if(restaurantModels.nonEmpty)
        originalSender ! Some(getListRestaurantStateBySeqRestaurantModels(restaurantModels))
      else originalSender ! None

      unstashAll()
      context.become(state())


    case _ =>
      stash()
  }

  def getAllReviewState(originalSender: ActorRef): Receive = {

    case GetReviewModelsResponse(reviewModels) =>
      if (reviewModels.nonEmpty)
        originalSender ! Some(getListReviewStateBySeqReviewModels(reviewModels))
      else originalSender ! None

      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }


  def getAllUserState(originalSender: ActorRef): Receive = {

    case GetUserModelsResponse(userModels) =>
      if (userModels.nonEmpty)
        originalSender ! Some(getListUserStateBySeqReviewModels(userModels))
      else originalSender ! None

      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}

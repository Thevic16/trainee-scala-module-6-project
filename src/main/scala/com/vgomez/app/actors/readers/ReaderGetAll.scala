package com.vgomez.app.actors.readers


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.readers.ReaderUtility._
import com.vgomez.app.data.projectionDatabase.Response.{GetRestaurantModelsResponse, GetReviewModelsResponse,
                                                    GetUserModelsResponse}
import com.vgomez.app.data.projectionDatabase.Operation

/*
Todo #R
  Description: Remove responses classes from actors.
  Action: Remove response class from ReaderGetAll Actor.
  Status: Done
  Reported by: Sebastian Oliveri.
*/
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
    /*
    Todo #1
      Description: Decouple restaurant.
      Action: Remove stars request on the database and only left restaurant models.
      Status: Done
      Reported by: Sebastian Oliveri.
    */
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

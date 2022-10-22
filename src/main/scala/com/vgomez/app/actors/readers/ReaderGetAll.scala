package com.vgomez.app.actors.readers


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Stash}
import akka.pattern.pipe
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse
import com.vgomez.app.actors.Review.Response.GetReviewResponse
import com.vgomez.app.actors.User.Response.GetUserResponse
import com.vgomez.app.actors.readers.ReaderUtility._
import com.vgomez.app.data.database.Model.RestaurantModel
import com.vgomez.app.data.database.Response.{GetRestaurantModelsResponse, GetReviewModelsResponse,
  GetSequenceReviewModelsStarsResponse, GetUserModelsResponse}
import com.vgomez.app.data.database.Operation

object ReaderGetAll {
  // commands
  object Command {
    case class CreateRestaurant(id: String)
    case class CreateReview(id: String)
    case class CreateUser(username: String)
    case class GetAllRestaurant(pageNumber: Long, numberOfElementPerPage: Long)
    case class GetAllReview(pageNumber: Long, numberOfElementPerPage: Long)
    case class GetAllUser(pageNumber: Long, numberOfElementPerPage: Long)
  }

  object Response {
    case class GetAllRestaurantResponse(optionGetRestaurantResponses: Option[List[GetRestaurantResponse]])
    case class GetAllReviewResponse(optionGetReviewResponses: Option[List[GetReviewResponse]])
    case class GetAllUserResponse(optionGetUserResponses: Option[List[GetUserResponse]])
  }

  def props(system: ActorSystem): Props =  Props(new ReaderGetAll(system))

}

class ReaderGetAll(system: ActorSystem) extends Actor with ActorLogging with Stash {

  import ReaderGetAll._
  import Command._
  import Response._
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
  
  def getAllRestaurantState(originalSender: ActorRef, restaurantModels: Seq[RestaurantModel] = Seq()): Receive = {

    case GetRestaurantModelsResponse(restaurantModels) =>
      val seqRestaurantId = restaurantModels.map(model => model.id)
      Operation.getReviewsStarsByListRestaurantId(
                                               seqRestaurantId).mapTo[GetSequenceReviewModelsStarsResponse].pipeTo(self)
      context.become(getAllRestaurantState(originalSender, restaurantModels))

    case GetSequenceReviewModelsStarsResponse(seqReviewModelsStars) =>
      originalSender ! GetAllRestaurantResponse(Some(getListRestaurantResponsesBySeqRestaurantModels(restaurantModels,
                                                    seqReviewModelsStars)))
      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  def getAllReviewState(originalSender: ActorRef): Receive = {

    case GetReviewModelsResponse(reviewModels) =>
      val optionGetReviewResponses: Option[List[GetReviewResponse]] =
                                                           Some(reviewModels.map(getReviewResponseByReviewModel).toList)
      originalSender ! GetAllReviewResponse(optionGetReviewResponses)

      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }


  def getAllUserState(originalSender: ActorRef): Receive = {

    case GetUserModelsResponse(userModels) =>
      val optionGetUserResponses: Option[List[GetUserResponse]] =
                                                                Some(userModels.map(getUserResponseByUserModel).toList)
      originalSender ! GetAllUserResponse(optionGetUserResponses)

      unstashAll()
      context.become(state())

    case _ =>
      stash()
  }

  override def receive: Receive = state()
}

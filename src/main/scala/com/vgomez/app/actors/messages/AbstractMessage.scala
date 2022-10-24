package com.vgomez.app.actors.messages

import akka.Done
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse

import scala.util.Try

object AbstractMessage {

  object Command {
    abstract class GetCommand

    abstract class CreateCommand

    abstract class UpdateCommand

    abstract class DeleteCommand
  }

  object Event {
    abstract class Event
  }

  object Response {
    abstract class GetResponse

    case class CreateResponse(maybeIdentifier: Try[String])

    case class UpdateResponse(maybeDone: Try[Done])

    case class DeleteResponse(maybeDone: Try[Done])

    case class GetRecommendationResponse(optionGetRestaurantResponses: Option[List[GetRestaurantResponse]])
  }

}

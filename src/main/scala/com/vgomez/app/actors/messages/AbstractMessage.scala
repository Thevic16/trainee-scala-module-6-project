package com.vgomez.app.actors.messages

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

    abstract class UpdateResponse

    case class DeleteResponse(maybeIdentifier: Try[String])

    case class GetRecommendationResponse(optionGetRestaurantResponses: Option[List[GetRestaurantResponse]])
  }

}

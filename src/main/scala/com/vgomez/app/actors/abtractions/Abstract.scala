package com.vgomez.app.actors.abtractions

import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse

import scala.util.Try

object Abstract {

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

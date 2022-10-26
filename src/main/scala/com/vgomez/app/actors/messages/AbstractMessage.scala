package com.vgomez.app.actors.messages

import akka.Done
import com.vgomez.app.actors.Restaurant.Response.GetRestaurantResponse

import scala.util.Try

/*
Todo #11
  Description: Change the generalized name of the Abstract class for more specific one.
  Status: Done
  Reported by: Nafer Sanabria.
*/
object AbstractMessage {

  object Command {
    abstract class GetCommand

    abstract class RegisterCommand

    abstract class UpdateCommand

    abstract class UnregisterCommand
  }

  object Event {
    abstract class Event
  }

  /*
  Todo #12
    Description: The sender doesn't need to receive redundant information as a response is better just to put a
                  Done object instead.
    State: Done
    Reported by: Sebastian Oliveri.
  */
  object Response {
    abstract class GetResponse

    case class RegisterResponse(maybeIdentifier: Try[String])

    case class UpdateResponse(maybeDone: Try[Done])

    case class UnregisterResponse(maybeDone: Try[Done])

    case class GetRecommendationResponse(optionGetRestaurantResponses: Option[List[GetRestaurantResponse]])
  }

}

package com.vgomez.app.actors.messages

import akka.persistence.journal.{Tagged, WriteEventAdapter}

object AbstractMessage {
  object Command {
    trait GetCommand

    trait RegisterCommand

    trait UpdateCommand

    trait UnregisterCommand
  }

  object Event {
    sealed trait Event

    trait EventAdministration extends Event
    sealed trait EventEntity extends Event

    trait EventRestaurant extends EventEntity
    trait EventReview extends EventEntity
    trait EventUser extends EventEntity

    val TagProjection = "event-for-projection"

    class EventProjectionAdapter extends WriteEventAdapter {
      override def manifest(event: Any): String = "eventProjectionAdapter"

      override def toJournal(event: Any): Any = event match {
        case event: EventEntity =>
          Tagged(event, Set(TagProjection))
        case event => event
      }
    }

  }

}

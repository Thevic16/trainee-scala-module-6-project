package com.vgomez.app.actors.messages

import akka.persistence.journal.{Tagged, WriteEventAdapter}

object AbstractMessage {
  object Command {
    abstract class GetCommand

    abstract class RegisterCommand

    abstract class UpdateCommand

    abstract class UnregisterCommand
  }

  object Event {
    /*
    Todo #5
      Description: Use projections to persist events on projection-db (Postgres) (Tag Events).
      Action: Reparate Event into two type.
      Status: Done
      Reported by: Sebastian Oliveri.
    */
    sealed trait Event

    trait EventAdministration extends Event
    sealed trait EventEntity extends Event

    trait EventRestaurant extends EventEntity
    trait EventReview extends EventEntity
    trait EventUser extends EventEntity

    val TagProjection = "event-for-projection"

    /*
    Todo #5
      Description: Use projections to persist events on projection-db (Postgres) (Tag Events).
      Action: Tag only normal events.
      Status: Done
      Reported by: Sebastian Oliveri.
    */
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

package com.vgomez.app.actors.writers

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.projection.ProjectionBehavior
import akka.projection.eventsourced.EventEnvelope

import com.vgomez.app.actors.messages.AbstractMessage.Event.{Event, TagProjection}


// Imports for akka projection
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.SourceProvider
import akka.actor.typed.scaladsl.adapter._

import akka.projection.ProjectionId
import akka.projection.cassandra.scaladsl.CassandraProjection

/*
Todo #2P
  Description: Use projections to persist events on projection-db (Postgres).
  Action: Modify WriterProjection to work using akka-projection library.
  Status: Done
  Reported by: Sebastian Oliveri.
*/
object WriterProjection {

  object Command {
    case object StartProjection
  }

  def props(system: ActorSystem): Props = Props(new WriterProjection(system))

}

class WriterProjection(system: ActorSystem) extends Actor with ActorLogging{
  import WriterProjection.Command._
  import system.dispatcher
  val typedSystem: akka.actor.typed.ActorSystem[_] = system.toTyped

  override def receive: Receive = {
    case StartProjection =>
      runProjection
      sender() ! Done
  }

  // Methods related with akka projection
  def getSourceProvider: SourceProvider[Offset, EventEnvelope[Event]] = {
    EventSourcedProvider
        .eventsByTag[Event](
          typedSystem,
          readJournalPluginId = CassandraReadJournal.Identifier,
          tag = TagProjection)
  }

  def runProjection: ActorRef[ProjectionBehavior.Command] = {
    val projection = CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("restaurant-reviews-projection", TagProjection),
      getSourceProvider,
      handler = () => new ProjectionHandler()
    )

    context.spawn(ProjectionBehavior(projection), projection.projectionId.id)
  }

}

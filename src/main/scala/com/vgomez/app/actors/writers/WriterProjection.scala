package com.vgomez.app.actors.writers

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.projection.ProjectionBehavior
import akka.projection.eventsourced.EventEnvelope

import com.vgomez.app.actors.messages.AbstractMessage.Event.{EventEntity, TagProjection}


// Imports for akka projection
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.SourceProvider
import akka.actor.typed.scaladsl.adapter._

import akka.projection.ProjectionId
import akka.projection.cassandra.scaladsl.CassandraProjection


object WriterProjection {

  object Command {
    case object StartProjection
  }

  def props(system: ActorSystem): Props = Props(new WriterProjection(system))

}

class WriterProjection(system: ActorSystem) extends Actor with ActorLogging{
  import WriterProjection.Command._
  val typedSystem: akka.actor.typed.ActorSystem[_] = system.toTyped

  def state(isStated: Boolean = false): Receive ={
    case StartProjection =>
      if(isStated){
        log.info("The projection process has been started already")
        sender() ! Done
      }
      else {
        runProjection
        log.info("Starting projection process")
        sender() ! Done
        context.become(state(true))
      }
  }

  override def receive: Receive = state()

  // Methods related with akka projection
  def getSourceProvider: SourceProvider[Offset, EventEnvelope[EventEntity]] = {
    EventSourcedProvider
        .eventsByTag[EventEntity](
          typedSystem,
          readJournalPluginId = CassandraReadJournal.Identifier,
          tag = TagProjection)
  }

  def runProjection: ActorRef[ProjectionBehavior.Command] = {
    val projection = CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("restaurant-reviews-projection", TagProjection),
      getSourceProvider,
      handler = () => new ProjectionHandler(typedSystem)
    )

    context.spawn(ProjectionBehavior(projection), projection.projectionId.id)
  }

}

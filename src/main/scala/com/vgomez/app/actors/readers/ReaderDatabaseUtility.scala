package com.vgomez.app.actors.readers

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery, Sequence}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Concat, Flow, Keep, RunnableGraph, Sink, Source}
import com.vgomez.app.actors.readers.ReaderDatabaseUtility.Response.GetEventsIdsResponse

import scala.concurrent.Future
object ReaderDatabaseUtility {
  object Response {
    case class GetEventsIdsResponse(ids: Set[String])
  }

  def getGraphReaderUtility(eventsWithSequenceSource: Source[EventEnvelope, NotUsed],
                      flow: Flow[Any, String, NotUsed]): RunnableGraph[Future[Seq[String]]] = {
    val eventsSource = eventsWithSequenceSource.map(_.event)
    val sink = Sink.seq[String]

    eventsSource.via(flow).toMat(sink)(Keep.right)
  }

}

case class ReaderDatabaseUtility(system: ActorSystem) {
  import ReaderDatabaseUtility._
  import system.dispatcher

  val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)
  implicit val materializer = ActorMaterializer()(system)

  def getSourceEventSByTag(tag: String): Source[EventEnvelope, NotUsed] = {
    queries.currentEventsByTag(tag = tag, offset = Sequence(0L))
  }

  def getSourceEventSByTagWithPagination(tag: String, pageNumber: Long,
                                         numberOfElementPerPage: Long): Source[EventEnvelope, NotUsed] = {
    queries.currentEventsByTag(tag = tag,
      offset = Sequence(numberOfElementPerPage * pageNumber)).take(numberOfElementPerPage)
  }

  def getSourceEventSByTagSet(set: Set[String]) : Source[EventEnvelope, NotUsed] = {
    val listEventsWithSequenceSource = set.toList.map(tag => queries.currentEventsByTag(
      tag = tag, offset = Sequence(0L)))

    listEventsWithSequenceSource.fold(Source.empty)(Source.combine(_, _)(Concat(_)))
  }

  def runGraph(graph: RunnableGraph[Future[Seq[String]]]): Future[GetEventsIdsResponse]  = {
    graph.run().map(ids => GetEventsIdsResponse(ids.toSet))
  }

  def runGraphWithPagination(graph: RunnableGraph[Future[Seq[String]]], pageNumber: Long,
                             numberOfElementPerPage: Long): Future[GetEventsIdsResponse] = {
    graph.run().map(seqIds => GetEventsIdsResponse(seqIds.toSet.slice((numberOfElementPerPage * pageNumber).toInt,
      (numberOfElementPerPage * pageNumber).toInt + numberOfElementPerPage.toInt)))
  }

}

package com.vgomez.app.data.projectionDatabase

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

/*
Todo
  Description: The reading approach of the application is very complicated, it should be better to use a second index
               database to read the information from there.
  State: Done
  Reported by: Sebastian Oliveri.
*/
object ExecContext {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(4))
}

object DataSetup {
  import Table.api._
  import Connection._

  def deleteData: Future[Seq[Int]] = {
    val combinedDelQueries = DBIO.sequence(Table.tables.map(_.delete))
    db.run(combinedDelQueries.transactionally)
  }

  def getDatabaseScript: Future[Unit] = {
    println(Table.ddl.createIfNotExistsStatements.mkString(";\n"))
    Future.unit
  }

}


package com.vgomez.app.data.database

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

object ExecContext {
  implicit val ec = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(4))
}

object DataSetup {
  import ExecContext._
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


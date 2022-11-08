
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.data.projectionDatabase

import scala.concurrent.Future

object DataSetup {

  import Connection._
  import Table.api._

  def deleteData: Future[Seq[Int]] = {
    val combinedDelQueries = DBIO.sequence(Table.tables.map(_.delete))
    db.run(combinedDelQueries.transactionally)
  }

  def getDatabaseScript: Future[Unit] = {
    println(Table.ddl.createIfNotExistsStatements.mkString(";\n"))
    Future.unit
  }

}



// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.data.projection

import com.vgomez.app.data.projection.Connection._
import com.vgomez.app.data.projection.Table.api._

import scala.concurrent.Future

object DataSetup {

  def deleteData: Future[Seq[Int]] = {
    val combinedDelQueries = DBIO.sequence(Table.tables.map(_.delete))
    db.run(combinedDelQueries.transactionally)
  }

  def getDatabaseScript: Future[Unit] = {
    println(Table.ddl.createIfNotExistsStatements.mkString(";\n"))
    Future.unit
  }

}


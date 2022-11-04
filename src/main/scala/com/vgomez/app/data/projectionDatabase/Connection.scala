
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.data.projectionDatabase

import com.typesafe.config.{Config, ConfigFactory}
import slick.jdbc.PostgresProfile.api._

object Connection {
  val conf: Config = ConfigFactory.load()

  val db = Database.forConfig(conf.getString("index-database.path"))
}

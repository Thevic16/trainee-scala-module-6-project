package com.vgomez.app.data.database

import com.typesafe.config.ConfigFactory
import slick.jdbc.PostgresProfile.api._

object Connection {
  val conf = ConfigFactory.load()

  val db = Database.forConfig(conf.getString("index-database.path"))
}

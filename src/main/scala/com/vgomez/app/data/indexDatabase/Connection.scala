package com.vgomez.app.data.indexDatabase

import com.typesafe.config.ConfigFactory
import slick.jdbc.PostgresProfile.api._
/*
Todo #1
  Description: The reading approach of the application is very complicated, it should be better to use a second index
               database to read the information from there.
  State: Done
  Action: Create Connection object
  Reported by: Sebastian Oliveri.
*/
object Connection {
  val conf = ConfigFactory.load()

  val db = Database.forConfig(conf.getString("index-database.path"))
}

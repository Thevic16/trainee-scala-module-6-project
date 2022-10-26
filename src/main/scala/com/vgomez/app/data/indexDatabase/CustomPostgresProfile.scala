package com.vgomez.app.data.indexDatabase

import com.github.tminglei.slickpg.ExPostgresProfile
import com.github.tminglei.slickpg._

/*
Todo #2
  Description: The reading approach of the application is very complicated, it should be better to use a second index
               database to read the information from there.
  State: Done
  Action: Create Custom Profile for Postgres to access special functionalities.
  Reported by: Sebastian Oliveri.
*/
trait CustomPostgresProfile extends ExPostgresProfile with PgArraySupport {
  override val api = CustomPGAPI

  object CustomPGAPI extends API with ArrayImplicits {
    implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)
  }
}

object CustomPostgresProfile extends CustomPostgresProfile

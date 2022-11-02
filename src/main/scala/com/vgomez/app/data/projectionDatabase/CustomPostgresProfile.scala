package com.vgomez.app.data.projectionDatabase

import com.github.tminglei.slickpg.ExPostgresProfile
import com.github.tminglei.slickpg._


trait CustomPostgresProfile extends ExPostgresProfile with PgArraySupport {
  override val api = CustomPGAPI

  object CustomPGAPI extends API with ArrayImplicits {
    implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)
  }
}

object CustomPostgresProfile extends CustomPostgresProfile

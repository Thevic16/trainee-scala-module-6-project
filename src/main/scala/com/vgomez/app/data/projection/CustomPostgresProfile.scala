
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.data.projection

import com.github.tminglei.slickpg._


trait CustomPostgresProfile extends ExPostgresProfile with PgArraySupport {
  override val api = CustomPGAPI

  object CustomPGAPI extends API with ArrayImplicits {
    implicit val strListTypeMapper: DriverJdbcType[List[String]] = new SimpleArrayJdbcType[String](
      "text")
      .to(_.toList)
  }
}

object CustomPostgresProfile extends CustomPostgresProfile

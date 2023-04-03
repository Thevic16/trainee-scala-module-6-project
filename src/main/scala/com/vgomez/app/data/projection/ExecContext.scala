
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.data.projection

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object ExecContext {
  implicit val ec: ExecutionContextExecutor = {
    val parallelism: Int = 4
    ExecutionContext.fromExecutor(Executors.newWorkStealingPool(parallelism))
  }
}

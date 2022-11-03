package com.vgomez.app.data.projectionDatabase

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object ExecContext {
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(4))
}

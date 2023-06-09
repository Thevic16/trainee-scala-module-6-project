
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.data.dataset

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout


class RunLoadDataSetGraph(filePath: String, chuck: Int, maxAmountRow: Int, administration: ActorRef,
  system: ActorSystem, timeout: Timeout) {
  def run(): Unit = {
    val loadDataset = new LoadDataset(filePath, chuck, maxAmountRow, administration, system, timeout)
    loadDataset.runLoadDataSetGraph()
  }
}

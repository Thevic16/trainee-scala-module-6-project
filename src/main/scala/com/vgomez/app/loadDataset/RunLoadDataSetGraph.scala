package com.vgomez.app.loadDataset
import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout


class RunLoadDataSetGraph(filePath: String, administration: ActorRef, system: ActorSystem, timeout: Timeout){
  def run() = {
    val loadDataset = new LoadDataset(filePath, administration, system, timeout)
    loadDataset.runLoadDataSetGraph()
  }
}

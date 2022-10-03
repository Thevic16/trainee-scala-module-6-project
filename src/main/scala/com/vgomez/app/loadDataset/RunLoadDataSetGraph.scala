package com.vgomez.app.loadDataset
import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory


class RunLoadDataSetGraph(filePath: String, administration: ActorRef, system: ActorSystem){
  def run() = {
    val loadDataset = new LoadDataset(filePath, administration, system)
    loadDataset.runLoadDataSetGraph()
  }
}

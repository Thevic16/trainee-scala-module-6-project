package com.vgomez.app.app

import akka.actor.{ActorRef, ActorSystem, Props}
import com.vgomez.app.http.RestaurantRouter

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.vgomez.app.actors.Administration

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object App {

  def startHttpServer(administration: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val scheduler: ExecutionContext = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val restaurantRouter = new RestaurantRouter(administration)
    val restaurantRoutes = restaurantRouter.routes

    val bindingFuture = Http().bindAndHandle(restaurantRoutes, "localhost", 8080)

    bindingFuture.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(s"Server online at http://${address.getHostString}:${address.getPort}")

      case Failure(exception) =>
        system.log.error(s"Failed to bing HTTP server, because: $exception")
        system.terminate()
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("BankApp")
    implicit val timeout: Timeout = Timeout(2.seconds)

    val administration = system.actorOf(Props[Administration], "administration-system")

    startHttpServer(administration)
  }
}

package com.vgomez.app.app

import akka.actor.{ActorRef, ActorSystem, Props}
import com.vgomez.app.http.{RecommendationFilterByCategoriesRouter, RestaurantRouter, ReviewRouter, UserRouter}

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.vgomez.app.actors.Administration

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
import akka.http.scaladsl.server.Directives._
import com.typesafe.config.ConfigFactory
import com.vgomez.app.loadDataset.RunLoadDataSetGraph

object RestaurantReviewApp {

  def startHttpServer(administration: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val scheduler: ExecutionContext = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val restaurantRouter = new RestaurantRouter(administration)
    val restaurantRoutes = restaurantRouter.routes

    val reviewRouter = new ReviewRouter(administration)
    val reviewRoutes = reviewRouter.routes

    val userRouter = new UserRouter(administration)
    val userRoutes = userRouter.routes

    val recommendationFilterByCategoriesRouter = new RecommendationFilterByCategoriesRouter(administration)
    val recommendationFilterByCategoriesRoutes = recommendationFilterByCategoriesRouter.routes

    val allRoutes = restaurantRoutes ~ reviewRoutes ~ userRoutes ~ recommendationFilterByCategoriesRoutes

    val bindingFuture = Http().bindAndHandle(allRoutes, "localhost", 8080)

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
    implicit val system: ActorSystem = ActorSystem("RestaurantReviewsApp")
    val timeout: Timeout = Timeout(2.seconds)

    val administration = system.actorOf(Administration.props(system), "administration-system")

    val conf = ConfigFactory.load()
    val runLoadDataSetGraph: Boolean = conf.getBoolean("load-dataset.run")

    if(runLoadDataSetGraph){
      val filePath = conf.getString("load-dataset.path-csv")
      new RunLoadDataSetGraph(filePath, administration, system, timeout).run()
    }

    startHttpServer(administration)
  }
}
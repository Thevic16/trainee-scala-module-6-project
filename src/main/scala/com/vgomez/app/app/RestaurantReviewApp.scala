
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.app

import akka.actor.{ActorRef, ActorSystem}
import com.vgomez.app.http.{RecommendationCategoriesRouter, RecommendationLocationsRouter,
                            RestaurantRouter, ReviewRouter, UserRouter}

import scala.concurrent.ExecutionContext
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.vgomez.app.actors.Administration

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
import akka.http.scaladsl.server.Directives._
import com.typesafe.config.ConfigFactory
import com.vgomez.app.data.dataset.RunLoadDataSetGraph

object RestaurantReviewApp {

  def startHttpServer(administration: ActorRef,timeout: Timeout)(implicit system: ActorSystem): Unit = {
    implicit val scheduler: ExecutionContext = system.dispatcher
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val timeoutRouter: Timeout = timeout

    val restaurantRouter = new RestaurantRouter(administration)
    val restaurantRoutes = restaurantRouter.routes

    val reviewRouter = new ReviewRouter(administration)
    val reviewRoutes = reviewRouter.routes

    val userRouter = new UserRouter(administration)
    val userRoutes = userRouter.routes

    val recommendationCategoriesRouter = new RecommendationCategoriesRouter(administration)
    val recommendationCategoriesRoutes = recommendationCategoriesRouter.routes

    val recommendationLocationsRouter = new RecommendationLocationsRouter(administration)
    val recommendationLocationsRoutes = recommendationLocationsRouter.routes

    val allRoutes = restaurantRoutes ~ reviewRoutes ~ userRoutes ~ recommendationCategoriesRoutes ~
                    recommendationLocationsRoutes

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
    val conf = ConfigFactory.load()
    implicit val system: ActorSystem = ActorSystem("RestaurantReviewsApp", ConfigFactory.load().getConfig(
                                                                      conf.getString("actor-system-config.path")))

    val timeout: Timeout = Timeout(conf.getInt("actor-system-config.timeout").seconds)

    val administration = system.actorOf(Administration.props(system), "administration-system")

    val runLoadDataSetGraph: Boolean = conf.getBoolean("load-dataset.run")

    if(runLoadDataSetGraph){
      val filePath: String = conf.getString("load-dataset.path-csv")
      val chuck: Int = conf.getInt("load-dataset.chuck")
      val maxAmountRow: Int = conf.getInt("load-dataset.max-amount-row")

      new RunLoadDataSetGraph(filePath, chuck, maxAmountRow, administration, system, timeout).run()
    }

    startHttpServer(administration, timeout)
  }
}

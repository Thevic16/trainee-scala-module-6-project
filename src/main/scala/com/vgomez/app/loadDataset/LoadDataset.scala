package com.vgomez.app.loadDataset
import com.github.tototoshi.csv._
import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import com.vgomez.app.actors.AdministrationPlayground.SimpleActor
import com.vgomez.app.actors.Restaurant.Command.{CreateRestaurant, GetRestaurant}
import com.vgomez.app.actors.Restaurant.RestaurantInfo
import com.vgomez.app.actors.Review.Command.{CreateReview, GetReview}
import com.vgomez.app.actors.Review.ReviewInfo
import com.vgomez.app.actors.User.Command.{CreateUser, GetUser}
import com.vgomez.app.actors.User.UserInfo
import com.vgomez.app.domain.DomainModel.{Location, Normal}
import com.vgomez.app.domain.Transformers.transformScheduleStringToSchedule

import java.io.File
import java.util.UUID
import scala.collection.immutable.HashMap
import com.vgomez.app.actors.{Administration, AdministrationPlayground}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt



object LoadDataset extends App{

    //val dir = System.getProperty("user.dir")
    //System.out.println("current dir = " + dir)

    val reader = CSVReader.open(new File("target/storage/dataset/postcovid_reviews.csv"))
    val readerStream = reader.toStreamWithHeaders
    println(readerStream.head)

    val readerStreamtakeTen = readerStream.take(10)
//    readerStreamtakeTen.foreach{row =>
//        println("user_id: "+ row("user_id"))
//        println("restaurant_id: "+ row("business_id"))
//        println("review_id: "+ row("review_id"))
//        println("")
//    }


    // Use Akka Stream to process the data
    implicit val system = ActorSystem("LoadDataset")
    implicit val materializer = ActorMaterializer()

    val source = Source(readerStream.take(10))
    val flow = Flow[Map[String, String]].map(row =>convertRowToMapCommands(row))

    implicit val timeout: Timeout = Timeout(10.seconds)
    implicit val scheduler: ExecutionContext = system.dispatcher

    val administration = system.actorOf(Props[Administration], "administration")
    val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

//    val sink = Sink.foreach[Map[String, Product]](commandsMap => {
////        administration ! commandsMap("createUserCommand")
////        administration ! commandsMap("createRestaurantCommand")
////        administration ! commandsMap("createReviewCommand")
//        println(commandsMap)
//    })

    val sink = Sink.foreach(println)

    //val graph = source.via(flow).to(sink).run()

    // Test that the graph save it the events.

//    // users
//    (administration ? GetUser("iBRhL3g62aQzZ7nrJc-KCg")).pipeTo(simpleActor)
//    (administration ? GetUser("os_bbAergdkI_OiYyLrlrg")).pipeTo(simpleActor)
//    (administration ? GetUser("evqTtztHLyPSy3n0KcrJYg")).pipeTo(simpleActor)
//    (administration ? GetUser("mcWU_qk2tGJcRBWf8ZFGQg")).pipeTo(simpleActor)
//
//    // restaurants
//    (administration ? GetRestaurant("2WRCcQATOe_Em0k61T6kvQ")).pipeTo(simpleActor)
//    (administration ? GetRestaurant("pKvT7mixZLS3bJoxzsVbPw")).pipeTo(simpleActor)
//    (administration ? GetRestaurant("7YwbfZF4j8yNSAJ0aGfOUA")).pipeTo(simpleActor)
//    (administration ? GetRestaurant("v1UzkU8lEWdjxq8byWFOKg")).pipeTo(simpleActor)
//
//    // reviews
//    (administration ? GetReview("cdz56-rOcu_8JgJ2CaVppw")).pipeTo(simpleActor)
//    (administration ? GetReview("bObYlm3T4m3ld7xNSuPWxg")).pipeTo(simpleActor)
//    (administration ? GetReview("vPFrlWYmxdUxjXS2JQquHQ")).pipeTo(simpleActor)
//    (administration ? GetReview("wvtTSr4GGGnNhfnj7K1R6g")).pipeTo(simpleActor)


  def convertRowToMapCommands(row: Map[String, String])  = {
    val locationField = Location(row("latitude").toDouble, row("longitude").toDouble)
    val categoriesField = row("categories").split(",").toSet

    val createUserCommand = CreateUser(
      UserInfo(username = row("user_id"),
        password = UUID.randomUUID().toString,
        role = Normal,
        location = locationField,
        favoriteCategories = categoriesField))

    val createRestaurantCommand = CreateRestaurant(maybeId = Some(row("business_id")), RestaurantInfo(
      userId = row("user_id"), name = row("name"), state = row("state_"), city = row("postal_code"),
      postalCode = row("postal_code"), location = locationField, categories = categoriesField,
      schedule = transformScheduleStringToSchedule(row("hours"))
    ))

    val createReviewCommand = CreateReview(maybeId = Some(row("review_id")),
      ReviewInfo(userId = row("user_id"), restaurantId = row("business_id"),
        stars = row("customer_stars").toInt, text = row("text"), date = row("date_")))


    Map("createUserCommand" -> createUserCommand, "createRestaurantCommand" -> createRestaurantCommand,
      "createReviewCommand" -> createReviewCommand)
  }

}

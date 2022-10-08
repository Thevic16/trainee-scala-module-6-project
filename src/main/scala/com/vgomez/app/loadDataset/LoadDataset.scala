package com.vgomez.app.loadDataset
import com.github.tototoshi.csv._
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import com.vgomez.app.actors.Restaurant.Command.CreateRestaurant
import com.vgomez.app.actors.Restaurant.RestaurantInfo
import com.vgomez.app.actors.Review.Command.CreateReview
import com.vgomez.app.actors.Review.ReviewInfo
import com.vgomez.app.actors.User.Command.CreateUser
import com.vgomez.app.actors.User.UserInfo
import com.vgomez.app.domain.DomainModel.{Location, Normal}
import com.vgomez.app.domain.Transformer.transformScheduleStringToSchedule

import java.io.File
import java.util.UUID

import scala.concurrent.ExecutionContext

class LoadDataset(filePath: String, administration: ActorRef, implicit val system: ActorSystem,
                  implicit val timeout: Timeout) {
    // Use Akka Stream to process the data
    implicit val materializer = ActorMaterializer()
    implicit val scheduler: ExecutionContext = system.dispatcher


  def runLoadDataSetGraph() = {
    /*
    Todo ask about why it only process a maximum of 15 row.
    */
    val readerStream = getReaderStream().take(15)
    println(s"readerStream size: ${readerStream.size}")
    // Graph
    val source = Source(readerStream)
    val flow = Flow[Map[String, String]].map(row => convertRowToMapCommands(row))
    val sink = Sink.foreach[Map[String, Product]](commandsMap => {
           administration ! commandsMap("createUserCommand")
           administration ! commandsMap("createRestaurantCommand")
           administration ! commandsMap("createReviewCommand")
        })
//    var i = 0
//    val sink = Sink.foreach{_: Map[String, Product] => {
//      i = i + 1
//      println(i)
//    }}
    source.via(flow).to(sink).run()
  }

  def getReaderStream(): Stream[Map[String, String]] = {
    val reader = CSVReader.open(new File(filePath))
    reader.toStreamWithHeaders
  }


  def convertRowToMapCommands(row: Map[String, String]): Map[String, Product]  = {
    val locationField: Location = Location(row("latitude").toDouble, row("longitude").toDouble)
    val categoriesField: Set[String] = row("categories").split(",").map(category => category.trim).toSet

    val createUserCommand = getCreateUserCommand(row, locationField, categoriesField)
    val createRestaurantCommand = getCreateRestaurantCommand(row, locationField, categoriesField)
    val createReviewCommand = getCreateReviewCommand(row)

    Map("createUserCommand" -> createUserCommand, "createRestaurantCommand" -> createRestaurantCommand,
      "createReviewCommand" -> createReviewCommand)
  }

  def getCreateUserCommand(row: Map[String, String], locationField: Location,
                           categoriesField: Set[String]): CreateUser = {
    CreateUser(UserInfo(username = row("user_id"),
      password = UUID.randomUUID().toString,
      role = Normal,
      location = locationField,
      favoriteCategories = categoriesField))
  }

  def getCreateRestaurantCommand(row: Map[String, String], locationField: Location,
                                 categoriesField: Set[String]): CreateRestaurant = {
    CreateRestaurant(maybeId = Some(row("business_id")), RestaurantInfo(
      userId = row("user_id"), name = row("name"), state = row("state_"), city = row("postal_code"),
      postalCode = row("postal_code"), location = locationField, categories = categoriesField,
      schedule = transformScheduleStringToSchedule(row("hours"))
    ))
  }

  def getCreateReviewCommand(row: Map[String, String]): CreateReview = {
    CreateReview(maybeId = Some(row("review_id")),
      ReviewInfo(userId = row("user_id"), restaurantId = row("business_id"),
        stars = row("customer_stars").toInt, text = row("text_"), date = row("date_")))
  }

}

package com.vgomez.app.loadDataset
import com.github.tototoshi.csv._
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, Materializer}
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

object LoadDataset{
  def convertRowToMapCommands(row: Map[String, String]): Map[String, Product] = {
    val locationField: Location = Location(row.getOrElse("latitude", "0").toDouble,
      row.getOrElse("longitude", "0").toDouble)

    val categoriesField: Set[String] = row.getOrElse("categories", "").split(",").map(category => category.trim).toSet
    val restaurantId: String = row.getOrElse("business_id", UUID.randomUUID().toString)
    val reviewId: String = row.getOrElse("review_id", UUID.randomUUID().toString)
    val username: String = row.getOrElse("user_id", UUID.randomUUID().toString)


    val createUserCommand = getCreateUserCommand(row, locationField, categoriesField)
    val createRestaurantCommand = getCreateRestaurantCommand(row, locationField, categoriesField, restaurantId, username)
    val createReviewCommand = getCreateReviewCommand(row, restaurantId, reviewId, username)

    Map("createUserCommand" -> createUserCommand, "createRestaurantCommand" -> createRestaurantCommand,
      "createReviewCommand" -> createReviewCommand)
  }

  def getCreateUserCommand(row: Map[String, String], locationField: Location,
                           categoriesField: Set[String]): CreateUser = {
    CreateUser(UserInfo(username = row.getOrElse("user_id", UUID.randomUUID().toString),
      password = UUID.randomUUID().toString,
      role = Normal,
      location = locationField,
      favoriteCategories = categoriesField))
  }

  def getCreateRestaurantCommand(row: Map[String, String], locationField: Location,
                                 categoriesField: Set[String], restaurantId: String, username: String): CreateRestaurant = {
    val defaultHours: String = "{'Monday': '0:0-0:0'}"

    CreateRestaurant(maybeId = Some(restaurantId), RestaurantInfo(
      username = username, name = row.getOrElse("name", "Unknown name"), state = row.getOrElse("state_", "Unknown state"),
      city = row.getOrElse("city", "Unknown city"), postalCode = row.getOrElse("postal_code", "UnKnown postal code"),
      location = locationField, categories = categoriesField,
      schedule = transformScheduleStringToSchedule(row.getOrElse("hours", defaultHours))
    ))
  }

  def getCreateReviewCommand(row: Map[String, String], restaurantId: String, reviewId: String,
                             username: String): CreateReview = {
    CreateReview(maybeId = Some(reviewId),
      ReviewInfo(username = username, restaurantId = restaurantId,
        stars = row.getOrElse("customer_stars", "0").toInt, text = row.getOrElse("text_", "No text"),
        date = row.getOrElse("date_", "Unknown date")))
  }

}

class LoadDataset(filePath: String, chuck: Int, maxAmountRow: Int, administration: ActorRef, implicit val system: ActorSystem,
                  implicit val timeout: Timeout) {
    import LoadDataset._
    // Use Akka Stream to process the data
    implicit val materializer = ActorMaterializer()
    implicit val scheduler: ExecutionContext = system.dispatcher


  def runLoadDataSetGraph(): Unit = {
    val readerStream = getReaderStream()

    def go(readerStream: Stream[Map[String, String]], counterRow: Int = 0): Unit = {
      if (readerStream.isEmpty) println(s"All the data ($counterRow rows) have be loaded.")
      else if (counterRow >= maxAmountRow && maxAmountRow != -1) {
        println(s"The specify amount of data ($counterRow rows) have be loaded.")
      }
      else {
        val readerStreamChuck = readerStream.take(chuck)
        val lastMapChuck = readerStreamChuck.last

        runChuckGraph(readerStreamChuck)
        go(readerStream.dropWhile(_ != lastMapChuck).tail, counterRow + chuck)
      }
    }

    go(readerStream)
  }

  def getReaderStream(): Stream[Map[String, String]] = {
    val reader = CSVReader.open(new File(filePath))
    reader.toStreamWithHeaders
  }

  def runChuckGraph(readerStream: Stream[Map[String, String]]): Unit = {
    val source = Source(readerStream)
    val flow = Flow[Map[String, String]].map(row => convertRowToMapCommands(row))
    val sink = Sink.foreach[Map[String, Product]](commandsMap => {
      administration ! commandsMap("createUserCommand")
      administration ! commandsMap("createRestaurantCommand")
      administration ! commandsMap("createReviewCommand")
    })

    source.via(flow).to(sink).run()
  }

}

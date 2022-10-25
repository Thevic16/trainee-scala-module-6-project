package com.vgomez.app.data.dataset

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout
import com.github.tototoshi.csv._
import com.vgomez.app.actors.Restaurant.Command.RegisterRestaurant
import com.vgomez.app.actors.Restaurant.RestaurantInfo
import com.vgomez.app.actors.Review.Command.RegisterReview
import com.vgomez.app.actors.Review.ReviewInfo
import com.vgomez.app.actors.User.Command.RegisterUser
import com.vgomez.app.actors.User.UserInfo
import com.vgomez.app.domain.DomainModel.{Location, Normal}
import com.vgomez.app.domain.Transformer.FromRawDataToDomain._

import java.io.File
import java.util.UUID
import scala.concurrent.ExecutionContext

/*
Todo
  Description: LoadDataset should be moved to a more appropriate package.
  Status: Done
  Reported by: Nafer Sanabria.
*/
object LoadDataset{
  def convertRowToMapCommands(row: Map[String, String]): Map[String, Product] = {
    val locationField: Location = Location(row.getOrElse("latitude", "0").toDouble,
      row.getOrElse("longitude", "0").toDouble)

    val categoriesField: Set[String] = row.getOrElse("categories", "").split(",").map(
                                                                                        category => category.trim).toSet
    val restaurantId: String = row.getOrElse("business_id", UUID.randomUUID().toString)
    val reviewId: String = row.getOrElse("review_id", UUID.randomUUID().toString)
    val username: String = row.getOrElse("user_id", UUID.randomUUID().toString)


    val registerUserCommand = getRegisterUserCommand(row, locationField, categoriesField)
    val registerRestaurantCommand = getRegisterRestaurantCommand(row, locationField, categoriesField, restaurantId,
                                                              username)
    val registerReviewCommand = getRegisterReviewCommand(row, restaurantId, reviewId, username)

    Map("registerUserCommand" -> registerUserCommand, "registerRestaurantCommand" -> registerRestaurantCommand,
      "registerReviewCommand" -> registerReviewCommand)
  }

  def getRegisterUserCommand(row: Map[String, String], locationField: Location,
                           categoriesField: Set[String]): RegisterUser = {
    RegisterUser(UserInfo(username = row.getOrElse("user_id", UUID.randomUUID().toString),
      password = UUID.randomUUID().toString,
      role = Normal,
      location = locationField,
      favoriteCategories = categoriesField))
  }

  def getRegisterRestaurantCommand(row: Map[String, String], locationField: Location,
                                 categoriesField: Set[String], restaurantId: String,
                                 username: String): RegisterRestaurant = {
    val defaultHours: String = "{'Monday': '0:0-0:0'}"

    RegisterRestaurant(maybeId = Some(restaurantId), RestaurantInfo(
      username = username, name = row.getOrElse("name", "Unknown name"),
      state = row.getOrElse("state_", "Unknown state"), city = row.getOrElse("city", "Unknown city"),
      postalCode = row.getOrElse("postal_code", "UnKnown postal code"),
      location = locationField, categories = categoriesField,
      schedule = transformScheduleStringToSchedule(row.getOrElse("hours", defaultHours))
    ))
  }

  def getRegisterReviewCommand(row: Map[String, String], restaurantId: String, reviewId: String,
                             username: String): RegisterReview = {
    RegisterReview(maybeId = Some(reviewId),
      ReviewInfo(username = username, restaurantId = restaurantId,
        stars = row.getOrElse("customer_stars", "0").toInt, text = row.getOrElse("text_", "No text"),
        date = row.getOrElse("date_", "Unknown date")))
  }

}

class LoadDataset(filePath: String, chuck: Int, maxAmountRow: Int, administration: ActorRef,
                  implicit val system: ActorSystem, implicit val timeout: Timeout) {
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
      administration ! commandsMap("registerUserCommand")
      administration ! commandsMap("registerRestaurantCommand")
      administration ! commandsMap("registerReviewCommand")
    })

    source.via(flow).to(sink).run()
  }

}

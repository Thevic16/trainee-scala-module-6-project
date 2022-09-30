package com.vgomez.app.actors
import akka.actor.Status.Success
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.vgomez.app.actors.Restaurant.RestaurantInfo
import com.vgomez.app.actors.Review.ReviewInfo
import com.vgomez.app.actors.User.UserInfo
import com.vgomez.app.domain.DomainModel._
import com.vgomez.app.domain.DomainModelFactory.generateNewEmptySchedule

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}


object AdministrationPlayground extends App {
  // Commands

  import Restaurant.Command._
  import Review.Command._
  import User.Command._

  // Responses
  import Restaurant.Response._
  import Review.Response._
  import User.Response._

  implicit val system: ActorSystem = ActorSystem("AdministrationPlayground")
  implicit val timeout: Timeout = Timeout(10.seconds)
  implicit val scheduler: ExecutionContext = system.dispatcher

  val administration = system.actorOf(Props[Administration], "administration")

  // ids
  var restaurantId = "83ff1fac-3603-4954-b4bd-1eadd78de41e"
  var reviewId = "d99803f4-4814-4fa7-95f5-d5c863995eb6"


  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message =>
        log.info(s"message: $message")
    }

  }

  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  // Administration

//  (administration ? CreateUser(UserInfo("admin", "admin",
//    Admin, Location(23.45, 53.25),
//    Set("China", "Frita")))).pipeTo(simpleActor)

//
//  (administration ? UpdateUser(UserInfo("admin", "admin",
//    Admin, Location(23, -23),
//    Set("Sushi", "Comida rapida")))).pipeTo(simpleActor)
//

  // (administration ? GetUser("admin")).pipeTo(simpleActor)

  //(administration ? DeleteUser("admin")).pipeTo(simpleActor)

  //(administration ? GetUser("admin")).pipeTo(simpleActor)




  //restaurant

//  (administration ? CreateRestaurant(RestaurantInfo("admin", "Sushi", "RD", "Santiago", "809",
//    Location(0,0), Set("Sushi"), generateNewEmptySchedule()))).pipeTo(simpleActor)
//

//   (administration ? UpdateRestaurant(restaurantId,RestaurantInfo("admin", "Sushi Ban", "RD mi pais", "Santiago0", "849",
//      Location(0,1), Set("Sushi", "Ensalada"), generateNewEmptySchedule()))).pipeTo(simpleActor)
//
//  (administration ? DeleteRestaurant(restaurantId)).pipeTo(simpleActor)
//
//  (administration ? GetRestaurant(restaurantId)).pipeTo(simpleActor)


  // Review
  // (administration ? CreateReview(ReviewInfo("admin",restaurantId, 5, "", "09-31-2022"))).pipeTo(simpleActor)

  (administration ? UpdateReview(reviewId ,ReviewInfo("admin",restaurantId, 4, "", "09-30-2022"))).pipeTo(simpleActor)

  (administration ? DeleteReview(reviewId)).pipeTo(simpleActor)

  (administration ? GetReview(reviewId)).pipeTo(simpleActor)
}

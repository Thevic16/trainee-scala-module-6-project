package com.vgomez.app.http.messages

import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import com.vgomez.app.domain.SimpleScheduler
import com.vgomez.app.domain.Transformer._

object HttpResponse{
  // Resquest clases
  case class RestaurantResponse(id: String, username: String, name: String, state: String, city: String,
                                postalCode: String, latitude: Double, longitude: Double, categories: Set[String],
                                timetable: Either[String, SimpleScheduler])
  trait RestaurantResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val restaurantResponseJson: RootJsonFormat[RestaurantResponse] = jsonFormat10(RestaurantResponse)
  }

  case class StarsResponse(stars: Int)

  trait StarsResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val starsResponseJson: RootJsonFormat[StarsResponse] = jsonFormat1(StarsResponse)
  }

  case class ReviewResponse(id: String, username: String, restaurantId: String, stars: Int, text: String, date: String)

  trait ReviewResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val reviewResponseJson: RootJsonFormat[ReviewResponse] = jsonFormat6(ReviewResponse)
  }

  case class UserResponse(username: String, password: String, role: String, latitude: Double, longitude: Double,
                          favoriteCategories: Set[String])

  trait UserResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val userResponseJson: RootJsonFormat[UserResponse] = jsonFormat6(UserResponse)
  }

  case class FailureResponse(reason: String)

  trait FailureResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val failureResponseJson: RootJsonFormat[FailureResponse] = jsonFormat1(FailureResponse)
  }

}

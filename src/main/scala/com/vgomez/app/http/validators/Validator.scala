
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.http.validators

import com.vgomez.app.domain.SimpleScheduler
import com.vgomez.app.exception.CustomException.ValidationFailException

import scala.util.{Failure, Success, Try}


object Validator {
  case class Valid(description: String)

  def validation(condition: Boolean, message: String): Unit = {
    if (condition) throw ValidationFailException(message)
  }

  def validateSimpleScheduler(schedule: SimpleScheduler): Boolean = {
    val regex: String = "[0-2][0-9]:[0-5][0-9]-[0-2][0-9]:[0-5][0-9]"
    if (schedule.monday.matches(regex) && schedule.tuesday.matches(regex) && schedule.wednesday.matches(regex)
      && schedule.thursday.matches(regex) && schedule.friday.matches(regex) &&
      schedule.saturday.matches(regex) && schedule.sunday.matches(regex)) {
      false
    }
    else {
      true
    }
  }

  def conditionLatitude(latitude: Double): Boolean = {
    val lowLimit: Int = -90
    val highLimit: Int = 90

    if (latitude >= lowLimit && latitude <= highLimit) {
      false
    }
    else {
      true
    }
  }

  def conditionLongitude(longitude: Double): Boolean = {
    val lowLimit: Int = -180
    val highLimit: Int = 180

    if (longitude >= lowLimit && longitude <= highLimit) {
      false
    }
    else {
      true
    }
  }
}

abstract class Validator {

  import Validator._

  def conditions(): Try[Valid]

  def run(): Try[Valid] = {
    try {
      conditions()
    }
    catch {
      case e: ValidationFailException =>
        Failure(e)
    }
  }
}

case class ValidatorRestaurantRequest(username: String, name: String, state: String, city: String,
  postalCode: String, latitude: Double, longitude: Double, categories: Set[String],
  schedule: SimpleScheduler) extends Validator {

  import Validator._

  override def conditions(): Try[Valid] = {
    validation(username.length < 5, "username length has to be greater than 5.")
    validation(name.isEmpty, "name should not be empty.")
    validation(state.length != 2, "state length has to be equal to 2.")
    validation(!state.matches("[A-Z]{2}"), "all character in state should be upper case.")
    validation(!city.matches("^[A-Z].*"), "city should start with a upper case letter.")
    validation(!postalCode.matches("[0-9]{5}"),
      "postalCode should consist of 5 numbers")
    validation(conditionLatitude(latitude), "latitude should be in the range of -90 to 90.")
    validation(conditionLongitude(longitude), "longitude should be in the range of -180 to 180.")
    validation(categories.isEmpty, "categories should no be empty.")
    validation(validateSimpleScheduler(schedule), "all members of schedule should follow the " +
      "format: [0-2][0-9]:[0-5][0-9]-[0-2][0-9]:[0-5][0-9]")

    Success(Valid("Restaurant Request has been validated"))
  }

}

case class ValidatorReviewRequest(username: String, restaurantId: String, stars: Int, text: String,
  date: String) extends Validator {

  import Validator._

  override def conditions(): Try[Valid] = {
    validation(username.length < 5, "username length has to be greater than 5.")
    validation(restaurantId.length < 5, "restaurantId length has to be greater than 5.")
    validation(!(stars >= 0 && stars <= 5), "stars should be in the range of [0-5].")
    validation(text.isEmpty, "text should not be empty.")
    validation(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-2][0-9]:[0-5][0-9]:[0-5][0-9]"),
      "date should follow the following format year-month-day hour:minute:second for example:" +
        " 2006-04-16 02:58:44")

    Success(Valid("Review Request has been validated"))
  }
}

case class ValidatorUserRequest(username: String, password: String, role: String, latitude: Double,
  longitude: Double, favoriteCategories: Set[String]) extends Validator {

  import Validator._

  override def conditions(): Try[Valid] = {
    validation(username.length < 5, "username length has to be greater than 5.")
    validation(password.length < 8, "password length has to be greater than 8.")
    validation(!role.matches("[Aa]dmin|[Nn]ormal"), "admin should follow the format" +
      " [Aa]dmin or [Nn]ormal.")
    validation(conditionLatitude(latitude), "latitude should be in the range of -90 to 90.")
    validation(conditionLongitude(longitude), "longitude should be in the range of -180 to 180.")
    validation(favoriteCategories.isEmpty, "favoriteCategories should no be empty.")
    Success(Valid("User Request has been validated"))
  }
}

case class ValidatorRequestWithPagination(pageNumber: Long, numberOfElementPerPage: Long) extends Validator {

  import Validator._

  override def conditions(): Try[Valid] = {
    validation(pageNumber < 0, "pageNumber parameter should be a positive number.")
    validation(numberOfElementPerPage < 0, "numberOfElementPerPage parameter should be a positive " +
      "number.")
    validation(numberOfElementPerPage > 100, "numberOfElementPerPage parameter should be greater " +
      "than 100.")

    Success(Valid("GetAllRequest has been validated"))
  }
}

case class ValidatorGetRecommendationFilterByFavoriteCategoriesRequest(favoriteCategories: Set[String])
  extends Validator {

  import Validator._

  override def conditions(): Try[Valid] = {
    validation(favoriteCategories.isEmpty, "favoriteCategories should no be empty.")
    Success(Valid("User GetRecommendationFilterByFavoriteCategoriesRequest has been validated"))
  }

}

case class ValidatorGetRecommendationFilterByUserFavoriteCategoriesRequest(username: String)
  extends Validator {

  import Validator._

  override def conditions(): Try[Valid] = {
    validation(username.length < 5, "username length has to be greater than 5.")
    Success(Valid("GetRecommendationFilterByUserFavoriteCategoriesRequest has been validated"))
  }

}

case class ValidatorGetRecommendationCloseToLocationRequest(latitude: Double, longitude: Double,
  rangeInKm: Double)
  extends Validator {

  import Validator._

  override def conditions(): Try[Valid] = {
    validation(conditionLatitude(latitude), "latitude should be in the range of -90 to 90.")
    validation(conditionLongitude(longitude), "longitude should be in the range of -180 to 180.")
    validation(rangeInKm < 0, "rangeInKm should be positive")

    Success(Valid("GetRecommendationCloseToLocationRequest has been validated"))
  }
}

case class ValidatorGetRecommendationCloseToMeRequest(username: String, rangeInKm: Double)
  extends Validator {

  import Validator._

  override def conditions(): Try[Valid] = {
    validation(username.length < 5, "username length has to be greater than 5.")
    validation(rangeInKm < 0, "rangeInKm should be positive")

    Success(Valid("GetRecommendationCloseToMeRequest has been validated"))
  }
}

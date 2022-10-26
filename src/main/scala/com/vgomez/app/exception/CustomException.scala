package com.vgomez.app.exception

object CustomException {
  /*
  Todo #14
    Description: Change the generalized names of the exceptions for more specific ones.
    Status: Done
    Reported by: Nafer Sanabria.
  */

  case class RestaurantNotFoundException(message: String =
                                         "The provided restaurant id was not found in the system.") extends
    RuntimeException(message)

  case class ReviewNotFoundException(message: String =
                                         "The provided review id was not found in the system.") extends
    RuntimeException(message)

  case class UserNotFoundException(message: String =
                                     "The provided username was not found in the system.") extends
    RuntimeException(message)

  case class RestaurantExistsException(message: String =
                                            "The provided restaurant already exist in the system.") extends
    RuntimeException(message)

  case class ReviewExistsException(message: String =
                                       "The provided review already exist in the system.") extends
    RuntimeException(message)

  case class UserExistsException(message: String =
                                       "The provided user already exist in the system.") extends
    RuntimeException(message)

  case object RestaurantUnRegisteredException extends
    RuntimeException("This restaurant is delete in the system.")

  case object ReviewUnRegisteredException extends
    RuntimeException("This review is delete in the system.")

  case object UserUnRegisteredException extends
    RuntimeException("This user is delete in the system.")

  case class ValidationFailException(message: String) extends IllegalArgumentException(message)
}

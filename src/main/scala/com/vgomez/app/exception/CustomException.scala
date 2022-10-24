package com.vgomez.app.exception

object CustomException {
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

  /*
  * Todo specify more clases for this later.
  * */
  case object EntityIsDeletedException extends
    RuntimeException("This entity is delete in the system.")

  case class ValidationFailException(message: String) extends IllegalArgumentException(message)

  case object UnknownConfigurationPathException extends
    RuntimeException("Unknown Actor system configuration path, please check application.conf actor-system-config section.")
}

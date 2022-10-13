package com.vgomez.app.exception

object CustomException {
  case class IdentifierNotFoundException(message: String =
                                         "The provided identifier was not found in the system.") extends
    RuntimeException(message)

  case class IdentifierExistsException(message: String =
                                            "The provided identifier already exist in the system.") extends
    RuntimeException(message)

  case object EntityIsDeletedException extends
    RuntimeException("This entity is delete in the system.")

  case class ValidationFailException(message: String) extends IllegalArgumentException(message)
}

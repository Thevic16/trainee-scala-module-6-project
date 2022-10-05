package com.vgomez.app.exception

object CustomException {
  case object IdentifierNotFoundException extends
    RuntimeException("The provided identifier was not found in the system.")

  case object UsernameExistsException extends
    RuntimeException("This username already exists in the system.")

  case object IdentifierExistsException extends
    RuntimeException("This Identifier already exists in the system.")

  case class ValidationFailException(message: String) extends IllegalArgumentException(message)
}

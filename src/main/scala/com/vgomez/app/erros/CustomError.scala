package com.vgomez.app.erros

object CustomError {
  case object IdentifierNotFoundException extends
    RuntimeException("The provided identifier was not found in the system.")

  case object UsernameExistsException extends
    RuntimeException("This username already exists in the system.")

}

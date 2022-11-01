package com.vgomez.app.actors.messages

object AbstractMessage {

  object Command {
    abstract class GetCommand

    abstract class RegisterCommand

    abstract class UpdateCommand

    abstract class UnregisterCommand
  }

  object Event {
    abstract class Event
  }

}

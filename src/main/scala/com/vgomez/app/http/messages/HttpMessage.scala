package com.vgomez.app.http.messages
import spray.json.DefaultJsonProtocol


object HttpResponse{
  case class FailureResponse(reason: String)

  trait FailureResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val failureResponseJson = jsonFormat1(FailureResponse)
  }
}

package com.vgomez.app.http

import spray.json.DefaultJsonProtocol
import spray.json.DefaultJsonProtocol.jsonFormat1

object HttpResponse {
  case class FailureResponse(reason: String)

  trait FailureResponseJsonProtocol extends DefaultJsonProtocol {
    implicit val failureResponseJson = jsonFormat1(FailureResponse)
  }

}

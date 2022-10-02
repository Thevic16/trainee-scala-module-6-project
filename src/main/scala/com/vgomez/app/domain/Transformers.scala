package com.vgomez.app.domain
import spray.json._

import scala.collection.immutable.HashMap


case class FromJsonScheduler(monday: String, tuesday: String, wednesday: String, thursday: String,
                             friday: String = "", saturday: String, sunday: String)

trait FromJsonSchedulerJsonProtocol extends DefaultJsonProtocol {
  implicit val fromJsonSchedulerFormat = jsonFormat7(FromJsonScheduler)
}

object Transformers extends FromJsonSchedulerJsonProtocol{
  import DomainModel._

  def transformHourStringToHour(hours: String): Hour = {
    val hrList = hours.split(":").toList
    Hour(hrList.head.toInt, hrList.tail.head.toInt)
  }

  def transformHoursStringToHashMapHour(hours: String): HashMap[String, Hour] = {
      val hoursList = hours.split("-").toList
      HashMap("startHour" -> transformHourStringToHour(hoursList.head),
        "endHour" -> transformHourStringToHour(hoursList.tail.head))
  }

  def transformFromJsonSchedulerDayToScheduleDay(scheduleDay:String, dayWeek: DayWeek): ScheduleDay = {
    if(scheduleDay != "") {
      val mondayHashMapHour = transformHoursStringToHashMapHour(scheduleDay)
      ScheduleDay(dayWeek, mondayHashMapHour("startHour"), mondayHashMapHour("endHour"))
    }
    else ScheduleDay(dayWeek, Hour(0,0), Hour(0,0))
  }

  def scheduleStringFormatter(scheduleString: String): String = {
    val scheduleStringJsonFormat = scheduleString.stripMargin.replace("'", "\"").toLowerCase

    val dayWeekList = List("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    def go(dayWeekList: List[String], scheduleStringJsonFormat: String): String = {
      if(dayWeekList.isEmpty) scheduleStringJsonFormat
      else {
        if (!scheduleStringJsonFormat.contains(dayWeekList.head)) {
          val  newScheduleStringJsonFormat= scheduleStringJsonFormat.replace(
            "}", "") + ",\""+dayWeekList.head+"\": \"0:0-0:0\" }"

          go(dayWeekList.tail, newScheduleStringJsonFormat)
        }
        else go(dayWeekList.tail, scheduleStringJsonFormat)
      }
    }

    go(dayWeekList, scheduleStringJsonFormat)
  }

  def transformScheduleStringToSchedule(scheduleString: String): Schedule = {
    val scheduleStringFormatted = scheduleStringFormatter(scheduleString)
    val fromJsonScheduler = scheduleStringFormatted.parseJson.convertTo[FromJsonScheduler]

    Schedule(Map(Monday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.monday, Monday),
      Tuesday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.tuesday, Tuesday),
      Wednesday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.wednesday, Wednesday),
      Thursday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.thursday, Thursday),
      Friday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.friday, Friday),
      Saturday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.saturday, Saturday),
      Sunday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.sunday, Sunday),
    ))
  }

}

object TransformersPlayground extends App {
  import Transformers._

//  println(transformHourStringToHour("0:0"))
//  println(transformHourStringToHour("22:0"))
//  println(transformHourStringToHour("1:15"))
//  println(transformHourStringToHour("11:30"))

  println(transformScheduleStringToSchedule("{'Monday': '1:0-5:0'}"))

}

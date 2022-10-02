package com.vgomez.app.domain
import spray.json._

import scala.collection.immutable.HashMap


case class SimpleScheduler(monday: String, tuesday: String, wednesday: String, thursday: String,
                             friday: String = "", saturday: String, sunday: String)

trait SimpleSchedulerJsonProtocol extends DefaultJsonProtocol {
  implicit val SimpleSchedulerFormat = jsonFormat7(SimpleScheduler)
}

object Transformers extends SimpleSchedulerJsonProtocol{
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

  def transformSimpleSchedulerToSchedule(fromJsonScheduler: SimpleScheduler): Schedule = {
    Schedule(Map(Monday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.monday, Monday),
      Tuesday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.tuesday, Tuesday),
      Wednesday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.wednesday, Wednesday),
      Thursday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.thursday, Thursday),
      Friday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.friday, Friday),
      Saturday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.saturday, Saturday),
      Sunday -> transformFromJsonSchedulerDayToScheduleDay(fromJsonScheduler.sunday, Sunday),
    ))
  }

  def transformScheduleStringToSchedule(scheduleString: String): Schedule = {
    val scheduleStringFormatted = scheduleStringFormatter(scheduleString)
    val fromJsonScheduler = scheduleStringFormatted.parseJson.convertTo[SimpleScheduler]

    transformSimpleSchedulerToSchedule(fromJsonScheduler)
  }

  // From Scheduler to JsonScheduler
  def transformScheduleDayWeekToString(schedule: Schedule, dayWeek: DayWeek): String = {
    val startHour: Hour = schedule.schedulesForDays(dayWeek).startHour
    val endHour: Hour = schedule.schedulesForDays(dayWeek).endHour
    s"${startHour.hr}:${startHour.minutes}-${endHour.hr}:${endHour.minutes}"
  }
  def transformScheduleToSimpleScheduler(schedule: Schedule): SimpleScheduler = {
    SimpleScheduler(monday = transformScheduleDayWeekToString(schedule,Monday),
      tuesday = transformScheduleDayWeekToString(schedule, Tuesday),
      wednesday = transformScheduleDayWeekToString(schedule, Wednesday),
      thursday = transformScheduleDayWeekToString(schedule, Thursday),
      friday = transformScheduleDayWeekToString(schedule, Friday),
      saturday = transformScheduleDayWeekToString(schedule, Saturday),
      sunday = transformScheduleDayWeekToString(schedule, Sunday))
  }

}

object TransformersPlayground extends App {
  import Transformers._

//  println(transformHourStringToHour("0:0"))
//  println(transformHourStringToHour("22:0"))
//  println(transformHourStringToHour("1:15"))
//  println(transformHourStringToHour("11:30"))

  //println(transformScheduleStringToSchedule("{'Monday': '1:0-5:0'}, 'Tuesday': '1:0-5:0'}"))

  println(transformScheduleStringToSchedule("{\"Monday\": \"1:0-5:0\"}, \"Tuesday\": \"1:0-5:0\"}"))

}

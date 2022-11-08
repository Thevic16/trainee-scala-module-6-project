
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.domain

import spray.json._

import scala.annotation.tailrec
import scala.collection.immutable.HashMap


case class SimpleScheduler(monday: String, tuesday: String, wednesday: String, thursday: String,
  friday: String, saturday: String, sunday: String)

trait SimpleSchedulerJsonProtocol extends DefaultJsonProtocol {
  implicit val SimpleSchedulerFormat: RootJsonFormat[SimpleScheduler] = jsonFormat7(SimpleScheduler)
}

object Transformer extends SimpleSchedulerJsonProtocol {

  import DomainModel._

  object FromRawDataToDomain {
    def transformStringRoleToRole(stringRole: String): Role = {
      stringRole match {
        case "admin" => Admin
        case "normal" => Normal
        case _ => Normal
      }
    }

    def transformTimetableStringToTimetable(timetableString: String): Timetable = {
      if (timetableString != "NULL") {
        val scheduleStringFormatted = scheduleStringFormatter(timetableString)
        val fromJsonScheduler = scheduleStringFormatted.parseJson.convertTo[SimpleScheduler]
        transformSimpleSchedulerToSchedule(fromJsonScheduler)
      }
      else {
        UnavailableTimetable
      }
    }

    def scheduleStringFormatter(scheduleString: String): String = {
      val scheduleStringJsonFormat = scheduleString.stripMargin.replace("'",
        "\"").toLowerCase

      val dayWeekList = List("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

      @tailrec
      def go(dayWeekList: List[String], scheduleStringJsonFormat: String): String = {
        if (dayWeekList.isEmpty) {
          scheduleStringJsonFormat
        }
        else {
          if (!scheduleStringJsonFormat.contains(dayWeekList.head)) {
            val newScheduleStringJsonFormat = scheduleStringJsonFormat.replace(
              "}", "") + ",\"" + dayWeekList.head + "\": \"0:0-0:0\" }"

            go(dayWeekList.tail, newScheduleStringJsonFormat)
          }
          else {
            go(dayWeekList.tail, scheduleStringJsonFormat)
          }
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

    def transformFromJsonSchedulerDayToScheduleDay(scheduleDay: String, dayWeek: DayWeek): ScheduleDay = {
      if (scheduleDay != "") {
        val mondayHashMapHour = transformHoursStringToHashMapHour(scheduleDay)
        ScheduleDay(dayWeek, mondayHashMapHour("startHour"), mondayHashMapHour("endHour"))
      }
      else {
        ScheduleDay(dayWeek, Hour(0, 0), Hour(0, 0))
      }
    }

    def transformHoursStringToHashMapHour(hours: String): HashMap[String, Hour] = {
      val hoursList = hours.split("-").toList
      HashMap("startHour" -> transformHourStringToHour(hoursList.head),
        "endHour" -> transformHourStringToHour(hoursList.tail.head))
    }

    def transformHourStringToHour(hours: String): Hour = {
      val hrList = hours.split(":").toList
      Hour(hrList.head.toInt, hrList.tail.head.toInt)
    }

  }


  object FromDomainToRawData {
    def transformRoleToStringRole(role: Role): String = {
      role match {
        case Admin => "admin"
        case Normal => "normal"
      }
    }

    def transformScheduleToSimpleScheduler(schedule: Schedule): SimpleScheduler = {
      SimpleScheduler(monday = transformScheduleDayWeekToString(schedule, Monday),
        tuesday = transformScheduleDayWeekToString(schedule, Tuesday),
        wednesday = transformScheduleDayWeekToString(schedule, Wednesday),
        thursday = transformScheduleDayWeekToString(schedule, Thursday),
        friday = transformScheduleDayWeekToString(schedule, Friday),
        saturday = transformScheduleDayWeekToString(schedule, Saturday),
        sunday = transformScheduleDayWeekToString(schedule, Sunday))
    }

    // From Scheduler to JsonScheduler
    def transformScheduleDayWeekToString(schedule: Schedule, dayWeek: DayWeek): String = {
      val startHour: Hour = schedule.schedulesForDays(dayWeek).startHour
      val endHour: Hour = schedule.schedulesForDays(dayWeek).endHour
      s"${startHour.hr}:${startHour.minutes}-${endHour.hr}:${endHour.minutes}"
    }

    def transformTimetableToString(timetable: Timetable): String = {
      timetable match {
        case schedule@Schedule(_) =>
          val simpleScheduler = SimpleScheduler(monday = transformScheduleDayToString(schedule
            .schedulesForDays(Monday)),
            tuesday = transformScheduleDayToString(schedule.schedulesForDays(Tuesday)),
            wednesday = transformScheduleDayToString(schedule.schedulesForDays(Wednesday)),
            thursday = transformScheduleDayToString(schedule.schedulesForDays(Thursday)),
            friday = transformScheduleDayToString(schedule.schedulesForDays(Friday)),
            saturday = transformScheduleDayToString(schedule.schedulesForDays(Saturday)),
            sunday = transformScheduleDayToString(schedule.schedulesForDays(Sunday)))

          simpleScheduler.toJson.toString()
        case UnavailableTimetable =>
          "NULL"
      }
    }

    def transformScheduleDayToString(scheduleDay: ScheduleDay): String = {
      s"${scheduleDay.startHour.hr}:${scheduleDay.startHour.minutes}-" +
        s"${scheduleDay.endHour.hr}:${scheduleDay.endHour.minutes}"
    }

  }

}

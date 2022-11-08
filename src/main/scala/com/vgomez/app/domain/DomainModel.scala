
// Copyright (C) 2022 Víctor Gómez.
package com.vgomez.app.domain

import com.vgomez.app.domain.DomainModel._

import scala.annotation.tailrec

object DomainModel {
  sealed trait Timetable

  sealed trait Role

  sealed class DayWeek

  case class Location(latitude: Double, longitude: Double)

  case class Hour(hr: Int, minutes: Int)

  case class ScheduleDay(dayWeek: DayWeek, startHour: Hour, endHour: Hour)

  case class Schedule(schedulesForDays: Map[DayWeek, ScheduleDay]) extends Timetable

  case object Monday extends DayWeek

  case object Tuesday extends DayWeek

  case object Wednesday extends DayWeek

  case object Thursday extends DayWeek

  case object Friday extends DayWeek

  case object Saturday extends DayWeek

  case object Sunday extends DayWeek

  case object UnavailableTimetable extends Timetable

  case object Normal extends Role

  case object Admin extends Role
}

object DomainModelFactory {

  import DomainModel._

  def generateNewEmptySchedule(): Schedule = {
    Schedule(Map(generateSchedulesForDaysElement(Monday),
      generateSchedulesForDaysElement(Tuesday),
      generateSchedulesForDaysElement(Wednesday),
      generateSchedulesForDaysElement(Thursday),
      generateSchedulesForDaysElement(Friday),
      generateSchedulesForDaysElement(Saturday),
      generateSchedulesForDaysElement(Sunday),
    ))
  }

  def generateSchedulesForDaysElement(dayWeek: DayWeek): (DayWeek, ScheduleDay) = {
    (dayWeek, ScheduleDay(dayWeek, Hour(hr = 0, minutes = 0), Hour(hr = 0, minutes = 0)))
  }

  @tailrec
  def updateSchedule(oldSchedule: Schedule, newSchedule: Schedule): Schedule = {
    if (newSchedule.schedulesForDays.isEmpty) {
      oldSchedule
    }
    else {
      updateSchedule(Schedule(oldSchedule.schedulesForDays + newSchedule.schedulesForDays.head),
        Schedule(newSchedule.schedulesForDays.tail))
    }
  }
}

object DomainModelOperation {

  private val averageRadiusOfEarthKm: Int = 6371

  // Haversine formula.
  def calculateDistanceInKm(location1: Location, location2Option: Option[Location]): Double = {
    if (location2Option.nonEmpty) {
      calculateDistanceInKmHelper(location1, location2Option.get)
    }
    else {
      Double.MaxValue
    }
  }

  private def calculateDistanceInKmHelper(location1: Location, location2: Location): Double = {
    val latDistance = Math.toRadians(location1.latitude - location2.latitude)
    val lngDistance = Math.toRadians(location1.longitude - location2.longitude)

    val sinLat = Math.sin(latDistance / 2)
    val sinLng = Math.sin(lngDistance / 2)

    val a = sinLat * sinLat + (Math.cos(Math.toRadians(location1.longitude)) *
      Math.cos(Math.toRadians(location2.longitude)) *
      sinLng * sinLng)

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

    averageRadiusOfEarthKm * c
  }

  def rangeInKmToDegrees(rangeInKm: Double): Double = {
    val oneDegreesInKm = 111
    rangeInKm / oneDegreesInKm
  }

}

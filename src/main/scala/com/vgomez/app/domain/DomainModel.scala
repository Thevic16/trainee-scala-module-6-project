package com.vgomez.app.domain

object DomainModel {
  case class Location(latitude: Double, longitude: Double)

  sealed class DayWeek
  case object Monday extends DayWeek
  case object Tuesday extends DayWeek
  case object Wednesday extends DayWeek
  case object Thursday extends DayWeek
  case object Friday extends DayWeek
  case object Saturday extends DayWeek
  case object Sunday extends DayWeek

  case class Hour(hr: Int, minutes: Int)
  case class ScheduleDay(dayWeek: DayWeek, startHour: Hour, endHour: Hour)
  case class Schedule(schedulesForDays: Map[DayWeek, ScheduleDay])

  sealed class Role
  case object Normal extends Role
  case object Admin extends Role
}

object DomainModelFactory {
  import DomainModel._

  def generateSchedulesForDaysElement(dayWeek: DayWeek): (DayWeek, ScheduleDay) = {
    (dayWeek, ScheduleDay(dayWeek, Hour(0,0), Hour(0,0)))
  }

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

  def updateSchedule(oldSchedule: Schedule, newSchedule: Schedule): Schedule = {
    if (newSchedule.schedulesForDays.isEmpty) oldSchedule
    else updateSchedule(Schedule(oldSchedule.schedulesForDays + newSchedule.schedulesForDays.head),
      Schedule(newSchedule.schedulesForDays.tail))
  }
}

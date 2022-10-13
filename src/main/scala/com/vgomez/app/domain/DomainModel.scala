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

object DomainModelOperation {
  import DomainModel._

  private val AVERAGE_RADIUS_OF_EARTH_KM = 6371

  // Haversine formula.
  def calculateDistanceInKm(location1: Location, location2: Location): Double = {
    val latDistance = Math.toRadians(location1.latitude - location2.latitude)
    val lngDistance = Math.toRadians(location1.longitude - location2.longitude)

    val sinLat = Math.sin(latDistance / 2)
    val sinLng = Math.sin(lngDistance / 2)

    val a = sinLat * sinLat + (Math.cos(Math.toRadians(location1.longitude)) *
        Math.cos(Math.toRadians(location2.longitude)) *
        sinLng * sinLng)

    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    
    (AVERAGE_RADIUS_OF_EARTH_KM * c)
  }

  def restaurantCategoriesIsContainsByQueryCategories(restaurantCategories: Set[String],
                                                      queryCategories: Set[String]): Boolean = {
    def go(restaurantCategories: Set[String]): Boolean = {
      if (restaurantCategories.isEmpty) false
      else if (queryCategories.contains(restaurantCategories.head)) true
      else go(restaurantCategories.tail)
    }

    go(restaurantCategories)
  }

}

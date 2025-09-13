package io.github.j5ik2o.pcqrses.domain.users

import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}

trait DateTime {
  def asLocalDateTime: LocalDateTime
  def asZonedDateTime(zoneId: ZoneId = ZoneId.systemDefault()): ZonedDateTime = asLocalDateTime.atZone(zoneId)
  def asInstant(zoneId: ZoneId = ZoneId.systemDefault()): Instant = asZonedDateTime(zoneId).toInstant
}

object DateTime {
  def apply(value: LocalDateTime): DateTime = DateTimeImpl(value)
  def unapply(dateTime: DateTime): Option[LocalDateTime] = Some(dateTime.asLocalDateTime)

  def now(): DateTime = DateTimeImpl(LocalDateTime.now())

  private case class DateTimeImpl(value: LocalDateTime) extends DateTime {
    override def asLocalDateTime: LocalDateTime = value
  }
}

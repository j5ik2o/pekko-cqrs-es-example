package io.github.j5ik2o.pcqrses.command.domain.basic

import java.time.format.DateTimeParseException
import java.time.{Instant, LocalDateTime, ZoneId, ZonedDateTime}

trait DateTime {
  def asLocalDateTime: LocalDateTime
  def asZonedDateTime(zoneId: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
    asLocalDateTime.atZone(zoneId)
  def asInstant(zoneId: ZoneId = ZoneId.systemDefault()): Instant = asZonedDateTime(
    zoneId).toInstant
  def toEpochMilli(zoneId: ZoneId = ZoneId.systemDefault()): Long = asInstant(zoneId).toEpochMilli
  def toSecondsAndNanos: (Long, Int) = {
    val instant = asInstant()
    (instant.getEpochSecond, instant.getNano)
  }
}

object DateTime {
  def apply(value: LocalDateTime): DateTime = DateTimeImpl(value)
  def unapply(dateTime: DateTime): Option[LocalDateTime] = Some(dateTime.asLocalDateTime)

  def now(): DateTime = DateTimeImpl(LocalDateTime.now())

  def from(value: String): DateTime = parseFromString(value) match {
    case Right(v) => v
    case Left(e) => throw new IllegalArgumentException(e.message)
  }

  def from(value: Instant, zoneId: ZoneId = ZoneId.systemDefault()): DateTime =
    apply(LocalDateTime.ofInstant(value, zoneId))
  def from(value: ZonedDateTime): DateTime = apply(value.toLocalDateTime)
  def fromSecondsAndNanos(
    seconds: Long,
    nanos: Int,
    zoneId: ZoneId = ZoneId.systemDefault()): DateTime =
    apply(LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos.toLong), zoneId))

  def parseFromString(value: String): Either[DateTimeError, DateTime] =
    try
      Right(DateTimeImpl(LocalDateTime.parse(value)))
    catch {
      case e: DateTimeParseException => Left(DateTimeError.InvalidFormat(e.getMessage))
    }

  private case class DateTimeImpl(value: LocalDateTime) extends DateTime {
    override def asLocalDateTime: LocalDateTime = value
  }
}

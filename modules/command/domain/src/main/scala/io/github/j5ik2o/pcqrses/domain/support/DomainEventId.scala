package io.github.j5ik2o.pcqrses.domain.support

import wvlet.airframe.ulid.ULID

trait AggregateEventId {
  def asString: String
}

object AggregateEventId {
  def apply(value: ULID): AggregateEventId = AggregateEventIdImpl(value)

  def from(value: String): AggregateEventId = AggregateEventIdImpl(ULID.fromString(value))

  def generate(): AggregateEventId = AggregateEventIdImpl(ULID.newULID)

  def parse(value: String): Either[Exception, AggregateEventId] =
    try
      Right(from(value))
    catch {
      case e: IllegalArgumentException => Left(e)
    }

  private final case class AggregateEventIdImpl(ulid: ULID) extends AggregateEventId {
    override def asString: String = ulid.toString
  }

}

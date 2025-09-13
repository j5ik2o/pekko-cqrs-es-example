package io.github.j5ik2o.pcqrses.command.domain.support

import wvlet.airframe.ulid.ULID

trait DomainEventId {
  def asString: String
}

object DomainEventId {
  def apply(value: ULID): DomainEventId = AggregateEventIdImpl(value)

  def from(value: String): DomainEventId = AggregateEventIdImpl(ULID.fromString(value))

  def generate(): DomainEventId = AggregateEventIdImpl(ULID.newULID)

  def parse(value: String): Either[Exception, DomainEventId] =
    try
      Right(from(value))
    catch {
      case e: IllegalArgumentException => Left(e)
    }

  private final case class AggregateEventIdImpl(ulid: ULID) extends DomainEventId {
    override def asString: String = ulid.toString
  }

}

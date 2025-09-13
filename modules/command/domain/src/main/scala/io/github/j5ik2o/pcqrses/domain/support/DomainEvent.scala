package io.github.j5ik2o.pcqrses.domain.support

import java.time.Instant

trait AggregateEvent {
  type AggregateIdType <: AggregateId
  def id: AggregateEventId
  def aggregateId: AggregateIdType
  def occurredAt: Instant
}

package io.github.j5ik2o.pcqrses.domain.support

import io.github.j5ik2o.pcqrses.domain.basic.DateTime

trait DomainEvent {
  type EntityIdType <: EntityId
  def id: DomainEventId
  def entityId: EntityIdType
  def occurredAt: DateTime
}

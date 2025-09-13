package io.github.j5ik2o.pcqrses.domain.users

import io.github.j5ik2o.pcqrses.domain.basic.DateTime
import io.github.j5ik2o.pcqrses.domain.support.{DomainEvent, DomainEventId}

enum UserAccountEvent extends DomainEvent {
  override type EntityIdType = UserAccountId
  case Created(
    id: DomainEventId,
    entityId: UserAccountId,
    name: UserAccountName,
    emailAddress: EmailAddress,
    occurredAt: DateTime
  )
  case Renamed(
    id: DomainEventId,
    entityId: UserAccountId,
    oldName: UserAccountName,
    newName: UserAccountName,
    occurredAt: DateTime
  )
  case Deleted(
    id: DomainEventId,
    entityId: UserAccountId,
    occurredAt: DateTime
  )
}

package io.github.j5ik2o.pcqrses.command.domain.users

import io.github.j5ik2o.pcqrses.command.domain.basic.DateTime
import io.github.j5ik2o.pcqrses.command.domain.support.{DomainEvent, DomainEventId}

enum UserAccountEvent extends DomainEvent {
  override type EntityIdType = UserAccountId
  case Created_V1(
    id: DomainEventId,
    entityId: UserAccountId,
    name: UserAccountName,
    emailAddress: EmailAddress,
    occurredAt: DateTime
  )

  case Renamed_V1(
    id: DomainEventId,
    entityId: UserAccountId,
    oldName: UserAccountName,
    newName: UserAccountName,
    occurredAt: DateTime
  )

  case Deleted_V1(
    id: DomainEventId,
    entityId: UserAccountId,
    occurredAt: DateTime
  )
}

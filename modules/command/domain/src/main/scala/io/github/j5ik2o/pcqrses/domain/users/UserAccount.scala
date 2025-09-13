package io.github.j5ik2o.pcqrses.domain.users

import io.github.j5ik2o.pcqrses.domain.basic.DateTime
import io.github.j5ik2o.pcqrses.domain.support.{DomainEventId, Entity}

trait UserAccount extends Entity {
  override type IdType = UserAccountId
  def id: UserAccountId
  def name: UserAccountName
  def emailAddress: EmailAddress
  def createdAt: DateTime
  def updatedAt: DateTime

  def rename(newName: UserAccountName): Either[RenameError, (UserAccount, UserAccountEvent)]
  def delete: Either[DeleteError, (UserAccount, UserAccountEvent)]
}

object UserAccount {
  def apply(
    id: UserAccountId,
    name: UserAccountName,
    emailAddress: EmailAddress,
    createdAt: DateTime = DateTime.now(),
    updatedAt: DateTime = DateTime.now()
  ): (UserAccount, UserAccountEvent) =
    (
      UserAccountImpl(id, false, name, emailAddress, createdAt, updatedAt),
      UserAccountEvent.Created(
        id = DomainEventId.generate(),
        entityId = id,
        name = name,
        emailAddress = emailAddress,
        occurredAt = DateTime.now()
      ))
  def unapply(
    self: UserAccount): Option[(UserAccountId, UserAccountName, EmailAddress, DateTime, DateTime)] =
    Some((self.id, self.name, self.emailAddress, self.createdAt, self.updatedAt))

  private final case class UserAccountImpl(
    id: UserAccountId,
    deleted: Boolean,
    name: UserAccountName,
    emailAddress: EmailAddress,
    createdAt: DateTime,
    updatedAt: DateTime
  ) extends UserAccount {
    override def rename(
      newName: UserAccountName): Either[RenameError, (UserAccount, UserAccountEvent)] =
      if (name == newName) {
        Left(RenameError.FamilyNameSame)
      } else {
        val updated = this.copy(name = newName, updatedAt = DateTime.now())
        val event = UserAccountEvent.Renamed(
          id = DomainEventId.generate(),
          entityId = id,
          oldName = name,
          newName = newName,
          occurredAt = DateTime.now()
        )
        Right((updated, event))
      }

    override def delete: Either[DeleteError, (UserAccount, UserAccountEvent)] =
      if (deleted) {
        Left(DeleteError.AlreadyDeleted)
      } else {
        val updated = copy(deleted = true, updatedAt = DateTime.now())
        val event = UserAccountEvent.Deleted(
          id = DomainEventId.generate(),
          entityId = id,
          occurredAt = DateTime.now()
        )
        Right((updated, event))
      }
  }
}

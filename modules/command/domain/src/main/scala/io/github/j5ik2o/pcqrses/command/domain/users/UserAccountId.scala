package io.github.j5ik2o.pcqrses.command.domain.users

import io.github.j5ik2o.pcqrses.command.domain.support.EntityId
import wvlet.airframe.ulid.{CrockfordBase32, ULID}

trait UserAccountId extends EntityId {
  def entityTypeName: String
  def asString: String
}

object UserAccountId {
  def apply(value: ULID): UserAccountId = UserAccountIdImpl(value)

  def unapply(self: UserAccountId): Option[String] = Some(self.asString)

  def from(value: String): UserAccountId = parseFromString(value) match {
    case Right(v) => v
    case Left(e) => throw new IllegalArgumentException(e.message)
  }

  def generate(): UserAccountId = UserAccountIdImpl(ULID.newULID)

  def parseFromString(value: String): Either[UserAccountIdError, UserAccountId] =
    if (value.isEmpty) {
      Left(UserAccountIdError.Empty)
    } else if (value.length != 26) {
      Left(UserAccountIdError.InvalidLength)
    } else if (!CrockfordBase32.isValidBase32(value)) {
      Left(UserAccountIdError.InvalidFormat)
    } else {
      Right(from(value))
    }

  private case class UserAccountIdImpl(ulid: ULID) extends UserAccountId {
    override def entityTypeName: String = "UserAccount"
    override def asString: String = ulid.toString
  }
}

package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.users.{
  UserAccountSnapshot,
  UserAccountStatus
}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.basic.UserAccountName as ProtoUserAccountName

import io.github.j5ik2o.pcqrses.command.domain.basic.DateTime
import io.github.j5ik2o.pcqrses.command.domain.users.{
  EmailAddress as DomainEmailAddress,
  FirstName,
  LastName,
  UserAccount as DomainUserAccount,
  UserAccountId as DomainUserAccountId,
  UserAccountName as DomainUserAccountName
}
import UserAccountAggregateState.{
  Created,
  Deleted,
  NotCreated
}
import org.apache.pekko.serialization.SerializerWithStringManifest

class UserAccountSnapshotSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 20001

  override def manifest(o: AnyRef): String =
    o match {
      case _: Created => "Created"
      case _: Deleted => "Deleted"
      case _: NotCreated =>
        throw new IllegalArgumentException("NotCreated state should not be serialized")
    }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val snapshot = o match {
      case Created(userAccount) =>
        UserAccountSnapshot(
          userAccountId = userAccount.id.asString,
          status = UserAccountStatus.CREATED,
          userName = Some(
            ProtoUserAccountName(
              userAccount.name.breachEncapsulationOfFirstName.asString,
              userAccount.name.breachEncapsulationOfLastName.asString
            )),
          emailAddress = userAccount.emailAddress.asString,
          createdAt = {
            val sn = userAccount.createdAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          },
          updatedAt = {
            val sn = userAccount.updatedAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          }
        )
      case Deleted(userAccount) =>
        // Deleted状態でも全情報を保持（リカバリ用）
        UserAccountSnapshot(
          userAccountId = userAccount.id.asString,
          status = UserAccountStatus.DELETED,
          userName = Some(
            ProtoUserAccountName(
              userAccount.name.breachEncapsulationOfFirstName.asString,
              userAccount.name.breachEncapsulationOfLastName.asString
            )),
          emailAddress = userAccount.emailAddress.asString,
          createdAt = {
            val sn = userAccount.createdAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          },
          updatedAt = {
            val sn = userAccount.updatedAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          }
        )
      case NotCreated(_) =>
        throw new IllegalArgumentException("NotCreated state should not be serialized")
    }
    snapshot.toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val snapshot = UserAccountSnapshot.parseFrom(bytes)
    snapshot.status match {
      case UserAccountStatus.CREATED =>
        val (userAccount, _) = DomainUserAccount(
          id = DomainUserAccountId.from(snapshot.userAccountId),
          name = DomainUserAccountName(
            FirstName(snapshot.userName.get.firstName),
            LastName(snapshot.userName.get.lastName)
          ),
          emailAddress = DomainEmailAddress(snapshot.emailAddress),
          createdAt = DateTime.fromSecondsAndNanos(
            snapshot.createdAt.get.seconds,
            snapshot.createdAt.get.nanos),
          updatedAt = DateTime.fromSecondsAndNanos(
            snapshot.updatedAt.get.seconds,
            snapshot.updatedAt.get.nanos)
        )
        Created(userAccount)
      case UserAccountStatus.DELETED =>
        // Deleted状態でも全情報から復元
        val (userAccount, _) = DomainUserAccount(
          id = DomainUserAccountId.from(snapshot.userAccountId),
          name = DomainUserAccountName(
            FirstName(snapshot.userName.get.firstName),
            LastName(snapshot.userName.get.lastName)
          ),
          emailAddress = DomainEmailAddress(snapshot.emailAddress),
          createdAt = DateTime.fromSecondsAndNanos(
            snapshot.createdAt.get.seconds,
            snapshot.createdAt.get.nanos),
          updatedAt = DateTime.fromSecondsAndNanos(
            snapshot.updatedAt.get.seconds,
            snapshot.updatedAt.get.nanos)
        )
        Deleted(userAccount)
      case UserAccountStatus.UNKNOWN | UserAccountStatus.Unrecognized(_) =>
        throw new IllegalArgumentException(s"Unexpected status: ${snapshot.status}")
    }
  }
}

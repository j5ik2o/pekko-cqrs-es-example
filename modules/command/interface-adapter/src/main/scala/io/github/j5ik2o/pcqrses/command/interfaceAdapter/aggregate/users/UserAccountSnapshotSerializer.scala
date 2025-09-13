package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.users.{
  CreatedSnapshot,
  DeletedSnapshot,
  NotCreatedSnapshot,
  UserAccountSnapshot
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
import UserAccountAggregateState.{Created, Deleted, NotCreated}
import org.apache.pekko.serialization.SerializerWithStringManifest

class UserAccountSnapshotSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 20001

  override def manifest(o: AnyRef): String =
    o match {
      case _: NotCreated => "NotCreated"
      case _: Created => "Created"
      case _: Deleted => "Deleted"
    }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val snapshot = o match {
      case NotCreated(id) =>
        UserAccountSnapshot(
          UserAccountSnapshot.State.NotCreated(
            NotCreatedSnapshot(
              userAccountId = id.asString
            )
          )
        )
      case Created(userAccount) =>
        UserAccountSnapshot(
          UserAccountSnapshot.State.Created(
            CreatedSnapshot(
              userAccountId = userAccount.id.asString,
              userName = Some(
                ProtoUserAccountName(
                  userAccount.name.breachEncapsulationOfFirstName.asString,
                  userAccount.name.breachEncapsulationOfLastName.asString
                )
              ),
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
          )
        )
      case Deleted(userAccount) =>
        UserAccountSnapshot(
          UserAccountSnapshot.State.Deleted(
            DeletedSnapshot(
              userAccountId = userAccount.id.asString,
              userName = Some(
                ProtoUserAccountName(
                  userAccount.name.breachEncapsulationOfFirstName.asString,
                  userAccount.name.breachEncapsulationOfLastName.asString
                )
              ),
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
          )
        )
    }
    snapshot.toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val snapshot = UserAccountSnapshot.parseFrom(bytes)
    snapshot.state match {
      case UserAccountSnapshot.State.NotCreated(notCreated) =>
        NotCreated(DomainUserAccountId.from(notCreated.userAccountId))

      case UserAccountSnapshot.State.Created(created) =>
        val (userAccount, _) = DomainUserAccount(
          id = DomainUserAccountId.from(created.userAccountId),
          name = DomainUserAccountName(
            FirstName(created.userName.get.firstName),
            LastName(created.userName.get.lastName)
          ),
          emailAddress = DomainEmailAddress(created.emailAddress),
          createdAt = DateTime.fromSecondsAndNanos(
            created.createdAt.get.seconds,
            created.createdAt.get.nanos),
          updatedAt =
            DateTime.fromSecondsAndNanos(created.updatedAt.get.seconds, created.updatedAt.get.nanos)
        )
        Created(userAccount)

      case UserAccountSnapshot.State.Deleted(deleted) =>
        val (userAccount, _) = DomainUserAccount(
          id = DomainUserAccountId.from(deleted.userAccountId),
          name = DomainUserAccountName(
            FirstName(deleted.userName.get.firstName),
            LastName(deleted.userName.get.lastName)
          ),
          emailAddress = DomainEmailAddress(deleted.emailAddress),
          createdAt = DateTime.fromSecondsAndNanos(
            deleted.createdAt.get.seconds,
            deleted.createdAt.get.nanos),
          updatedAt =
            DateTime.fromSecondsAndNanos(deleted.updatedAt.get.seconds, deleted.updatedAt.get.nanos)
        )
        Deleted(userAccount)

      case UserAccountSnapshot.State.Empty =>
        throw new IllegalArgumentException("Unexpected empty state in UserAccountSnapshot")
    }
  }
}

package io.github.j5ik2o.pcqrses.interfaceAdapter.users

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.users.{UserAccountSnapshot, UserAccount_CreatedSnapshot, UserAccount_DeletedSnapshot, UserAccount_NotCreatedSnapshot, UserAccountName as ProtoUserAccountName}
import io.github.j5ik2o.pcqrses.domain.basic.DateTime
import io.github.j5ik2o.pcqrses.domain.users.{EmailAddress as DomainEmailAddress, FirstName, LastName, UserAccount as DomainUserAccount, UserAccountId as DomainUserAccountId, UserAccountName as DomainUserAccountName}
import io.github.j5ik2o.pcqrses.interfaceAdapter.users.UserAccountAggregateState.{Created, Deleted, NotCreated}
import org.apache.pekko.serialization.SerializerWithStringManifest

class UserAccountSnapshotSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 20001

  override def manifest(o: AnyRef): String = {
    o match {
      case _: NotCreated => "NotCreated"
      case _: Created => "Created"
      case _: Deleted => "Deleted"
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val snapshot = o match {
      case NotCreated(id) =>
        UserAccountSnapshot(
          state = UserAccountSnapshot.State.NotCreated(UserAccount_NotCreatedSnapshot(id.asString))
        )
      case Created(userAccount) =>
        UserAccountSnapshot(
          state = UserAccountSnapshot.State.Created(
            UserAccount_CreatedSnapshot(
              userAccountId = userAccount.id.asString,
              userName = Some(ProtoUserAccountName(userAccount.name.breachEncapsulationOfFirstName.asString, userAccount.name.breachEncapsulationOfLastName.asString)),
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
          ))
      case Deleted(id) =>
        UserAccountSnapshot(
          state = UserAccountSnapshot.State.Deleted(
            UserAccount_DeletedSnapshot(
              userAccountId = id.asString,
            )
          ))
    }
    snapshot.toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    UserAccountSnapshot.parseFrom(bytes).state match {
      case UserAccountSnapshot.State.NotCreated(value) =>
        NotCreated(DomainUserAccountId.from(value.userAccountId))
      case UserAccountSnapshot.State.Created(value) =>
        val (userAccount, _) = DomainUserAccount(
          id = DomainUserAccountId.from(value.userAccountId),
          name = DomainUserAccountName(
            FirstName(value.userName.get.firstName),
            LastName(value.userName.get.lastName)
          ),
          emailAddress = DomainEmailAddress(value.emailAddress),
          createdAt = DateTime.fromSecondsAndNanos(value.createdAt.get.seconds, value.createdAt.get.nanos),
          updatedAt = DateTime.fromSecondsAndNanos(value.updatedAt.get.seconds, value.updatedAt.get.nanos),
        )
        Created(userAccount)
      case UserAccountSnapshot.State.Deleted(value) =>
        Deleted(DomainUserAccountId.from(value.userAccountId))
      case UserAccountSnapshot.State.Empty =>
        throw new IllegalArgumentException("Unexpected Empty state in snapshot")
    }
  }
}

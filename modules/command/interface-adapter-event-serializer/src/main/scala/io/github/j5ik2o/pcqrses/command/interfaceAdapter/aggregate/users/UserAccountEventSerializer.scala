package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import io.github.j5ik2o.pcqrses.command.domain.basic.DateTime
import io.github.j5ik2o.pcqrses.command.domain.support.DomainEventId
import io.github.j5ik2o.pcqrses.command.domain.users.{FirstName, LastName, EmailAddress as DomainEmailAddress, UserAccountEvent as DomainUserAccountEvent, UserAccountId as DomainUserAccountId, UserAccountName as DomainUserAccountName}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.basic.UserAccountName as ProtoUserAccountName
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.users.*
import org.apache.pekko.serialization.SerializerWithStringManifest

class UserAccountEventSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 20002

  // マニフェストはEnvelope内のtype/versionと重複するため固定文字列に統一
  override def manifest(o: AnyRef): String = "Envelope"

  override def toBinary(o: AnyRef): Array[Byte] = {
    val envelope: UserAccountEvent_Envelope = o match {
      case DomainUserAccountEvent.Created_V1(id, entityId, name, emailAddress, occurredAt) =>
        val payload = UserAccountEvent_Created_V1(
          eventId = id.asString,
          userAccountId = entityId.asString,
          userName = Some(ProtoUserAccountName(
            name.breachEncapsulationOfFirstName.asString,
            name.breachEncapsulationOfLastName.asString
          )),
          emailAddress = emailAddress.asString,
          occurredAt = {
            val sn = occurredAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          }
        )
        UserAccountEvent_Envelope(
          userAccountId = entityId.asString,
          eventTypeName = "UserAccountEvent.Created",
          eventTypeVersion = "V1",
          payload = com.google.protobuf.ByteString.copyFrom(payload.toByteArray),
          occurredAt = {
            val sn = occurredAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          }
        )

      case DomainUserAccountEvent.Renamed_V1(id, entityId, oldName, newName, occurredAt) =>
        val payload = UserAccountEvent_Renamed_V1(
          eventId = id.asString,
          userAccountId = entityId.asString,
          oldName = Some(ProtoUserAccountName(
            oldName.breachEncapsulationOfFirstName.asString,
            oldName.breachEncapsulationOfLastName.asString
          )),
          newName = Some(ProtoUserAccountName(
            newName.breachEncapsulationOfFirstName.asString,
            newName.breachEncapsulationOfLastName.asString
          )),
          occurredAt = {
            val sn = occurredAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          }
        )
        UserAccountEvent_Envelope(
          userAccountId = entityId.asString,
          eventTypeName = "UserAccountEvent.Renamed",
          eventTypeVersion = "V1",
          payload = com.google.protobuf.ByteString.copyFrom(payload.toByteArray),
          occurredAt = {
            val sn = occurredAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          }
        )

      case DomainUserAccountEvent.Deleted_V1(id, entityId, occurredAt) =>
        val payload = UserAccountEvent_Deleted_V1(
          eventId = id.asString,
          userAccountId = entityId.asString,
          occurredAt = {
            val sn = occurredAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          }
        )
        UserAccountEvent_Envelope(
          userAccountId = entityId.asString,
          eventTypeName = "UserAccountEvent.Deleted",
          eventTypeVersion = "V1",
          payload = com.google.protobuf.ByteString.copyFrom(payload.toByteArray),
          occurredAt = {
            val sn = occurredAt.toSecondsAndNanos
            Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
          }
        )
    }
    envelope.toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val envelope = UserAccountEvent_Envelope.parseFrom(bytes)
    (envelope.eventTypeName, envelope.eventTypeVersion) match {
      case ("UserAccountEvent.Created", "V1") =>
        val value = UserAccountEvent_Created_V1.parseFrom(envelope.payload.toByteArray)
        DomainUserAccountEvent.Created_V1(
          id = DomainEventId.from(value.eventId),
          entityId = DomainUserAccountId.from(value.userAccountId),
          name = DomainUserAccountName(
            FirstName(value.userName.get.firstName),
            LastName(value.userName.get.lastName)
          ),
          emailAddress = DomainEmailAddress(value.emailAddress),
          occurredAt = DateTime.fromSecondsAndNanos(value.occurredAt.get.seconds, value.occurredAt.get.nanos)
        )

      case ("UserAccountEvent.Renamed", "V1") =>
        val value = UserAccountEvent_Renamed_V1.parseFrom(envelope.payload.toByteArray)
        DomainUserAccountEvent.Renamed_V1(
          id = DomainEventId.from(value.eventId),
          entityId = DomainUserAccountId.from(value.userAccountId),
          oldName = DomainUserAccountName(
            FirstName(value.oldName.get.firstName),
            LastName(value.oldName.get.lastName)
          ),
          newName = DomainUserAccountName(
            FirstName(value.newName.get.firstName),
            LastName(value.newName.get.lastName)
          ),
          occurredAt = DateTime.fromSecondsAndNanos(value.occurredAt.get.seconds, value.occurredAt.get.nanos)
        )

      case ("UserAccountEvent.Deleted", "V1") =>
        val value = UserAccountEvent_Deleted_V1.parseFrom(envelope.payload.toByteArray)
        DomainUserAccountEvent.Deleted_V1(
          id = DomainEventId.from(value.eventId),
          entityId = DomainUserAccountId.from(value.userAccountId),
          occurredAt = DateTime.fromSecondsAndNanos(value.occurredAt.get.seconds, value.occurredAt.get.nanos)
        )

      case (name, ver) =>
        throw new IllegalArgumentException(s"Unexpected event type: name=$name, version=$ver in UserAccount_Envelope")
    }
  }
}

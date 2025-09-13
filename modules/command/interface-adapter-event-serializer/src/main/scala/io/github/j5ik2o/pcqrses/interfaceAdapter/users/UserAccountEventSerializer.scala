package io.github.j5ik2o.pcqrses.interfaceAdapter.users

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.users.{
  UserAccountEvent as ProtoUserAccountEvent,
  UserAccount_CreatedEvent,
  UserAccount_RenamedEvent,
  UserAccount_DeletedEvent
}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.basic.UserAccountName as ProtoUserAccountName

import io.github.j5ik2o.pcqrses.domain.basic.DateTime
import io.github.j5ik2o.pcqrses.domain.support.DomainEventId
import io.github.j5ik2o.pcqrses.domain.users.{
  EmailAddress as DomainEmailAddress,
  FirstName,
  LastName,
  UserAccountEvent as DomainUserAccountEvent,
  UserAccountId as DomainUserAccountId,
  UserAccountName as DomainUserAccountName
}
import org.apache.pekko.serialization.SerializerWithStringManifest

class UserAccountEventSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 20002

  override def manifest(o: AnyRef): String = {
    o match {
      case _: DomainUserAccountEvent.Created => "Created"
      case _: DomainUserAccountEvent.Renamed => "Renamed"
      case _: DomainUserAccountEvent.Deleted => "Deleted"
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = {
    val protoEvent = o match {
      case DomainUserAccountEvent.Created(id, entityId, name, emailAddress, occurredAt) =>
        ProtoUserAccountEvent(
          event = ProtoUserAccountEvent.Event.Created(
            UserAccount_CreatedEvent(
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
          )
        )
      case DomainUserAccountEvent.Renamed(id, entityId, oldName, newName, occurredAt) =>
        ProtoUserAccountEvent(
          event = ProtoUserAccountEvent.Event.Renamed(
            UserAccount_RenamedEvent(
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
          )
        )
      case DomainUserAccountEvent.Deleted(id, entityId, occurredAt) =>
        ProtoUserAccountEvent(
          event = ProtoUserAccountEvent.Event.Deleted(
            UserAccount_DeletedEvent(
              eventId = id.asString,
              userAccountId = entityId.asString,
              occurredAt = {
                val sn = occurredAt.toSecondsAndNanos
                Some(com.google.protobuf.timestamp.Timestamp(sn._1, sn._2))
              }
            )
          )
        )
    }
    protoEvent.toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    ProtoUserAccountEvent.parseFrom(bytes).event match {
      case ProtoUserAccountEvent.Event.Created(value) =>
        DomainUserAccountEvent.Created(
          id = DomainEventId.from(value.eventId),
          entityId = DomainUserAccountId.from(value.userAccountId),
          name = DomainUserAccountName(
            FirstName(value.userName.get.firstName),
            LastName(value.userName.get.lastName)
          ),
          emailAddress = DomainEmailAddress(value.emailAddress),
          occurredAt = DateTime.fromSecondsAndNanos(value.occurredAt.get.seconds, value.occurredAt.get.nanos)
        )
      case ProtoUserAccountEvent.Event.Renamed(value) =>
        DomainUserAccountEvent.Renamed(
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
      case ProtoUserAccountEvent.Event.Deleted(value) =>
        DomainUserAccountEvent.Deleted(
          id = DomainEventId.from(value.eventId),
          entityId = DomainUserAccountId.from(value.userAccountId),
          occurredAt = DateTime.fromSecondsAndNanos(value.occurredAt.get.seconds, value.occurredAt.get.nanos)
        )
      case ProtoUserAccountEvent.Event.Empty =>
        throw new IllegalArgumentException("Unexpected Empty event in UserAccountEvent")
    }
  }
}
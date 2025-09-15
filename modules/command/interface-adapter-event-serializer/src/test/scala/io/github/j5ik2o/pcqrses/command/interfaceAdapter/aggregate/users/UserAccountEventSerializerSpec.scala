package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import io.github.j5ik2o.pcqrses.command.domain.basic.DateTime
import io.github.j5ik2o.pcqrses.command.domain.support.DomainEventId
import io.github.j5ik2o.pcqrses.command.domain.users.{
  EmailAddress,
  FirstName,
  LastName,
  UserAccountEvent,
  UserAccountId,
  UserAccountName
}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.users.UserAccountEvent as ProtoUserAccountEvent
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

class UserAccountEventSerializerSpec extends AnyFunSuiteLike with Matchers {

  private val serializer = new UserAccountEventSerializer

  test("identifier should be constant") {
    serializer.identifier shouldBe 20002
  }

  test("manifest should return expected strings") {
    val id = DomainEventId.generate()
    val entityId = UserAccountId.generate()
    val name = UserAccountName(FirstName("Taro"), LastName("Yamada"))
    val email = EmailAddress("taro.yamada@example.com")
    val occurredAt = DateTime.fromSecondsAndNanos(1710000000L, 123456789)

    serializer.manifest(UserAccountEvent.Created(id, entityId, name, email, occurredAt)) shouldBe "Created"
    serializer.manifest(UserAccountEvent.Renamed(id, entityId, name, name, occurredAt)) shouldBe "Renamed"
    serializer.manifest(UserAccountEvent.Deleted(id, entityId, occurredAt)) shouldBe "Deleted"
  }

  test("toBinary should encode Created correctly") {
    val id = DomainEventId.generate()
    val entityId = UserAccountId.generate()
    val name = UserAccountName(FirstName("Hanako"), LastName("Suzuki"))
    val email = EmailAddress("hanako.suzuki@example.com")
    val occurredAt = DateTime.fromSecondsAndNanos(1720000000L, 111222333)

    val ev = UserAccountEvent.Created(id, entityId, name, email, occurredAt)
    val bytes = serializer.toBinary(ev)
    val proto = ProtoUserAccountEvent.parseFrom(bytes)

    proto.event.isCreated shouldBe true
    val c = proto.getCreated
    c.eventId shouldBe id.asString
    c.userAccountId shouldBe entityId.asString
    c.userName.get.firstName shouldBe name.breachEncapsulationOfFirstName.asString
    c.userName.get.lastName shouldBe name.breachEncapsulationOfLastName.asString
    c.emailAddress shouldBe email.asString
    (c.occurredAt.get.seconds, c.occurredAt.get.nanos) shouldBe occurredAt.toSecondsAndNanos
  }

  test("toBinary should encode Renamed correctly") {
    val id = DomainEventId.generate()
    val entityId = UserAccountId.generate()
    val oldName = UserAccountName(FirstName("Bob"), LastName("Johnson"))
    val newName = UserAccountName(FirstName("Robert"), LastName("Johnson"))
    val occurredAt = DateTime.fromSecondsAndNanos(1730000000L, 222333444)

    val ev = UserAccountEvent.Renamed(id, entityId, oldName, newName, occurredAt)
    val bytes = serializer.toBinary(ev)
    val proto = ProtoUserAccountEvent.parseFrom(bytes)

    proto.event.isRenamed shouldBe true
    val r = proto.getRenamed
    r.eventId shouldBe id.asString
    r.userAccountId shouldBe entityId.asString
    r.oldName.get.firstName shouldBe oldName.breachEncapsulationOfFirstName.asString
    r.oldName.get.lastName shouldBe oldName.breachEncapsulationOfLastName.asString
    r.newName.get.firstName shouldBe newName.breachEncapsulationOfFirstName.asString
    r.newName.get.lastName shouldBe newName.breachEncapsulationOfLastName.asString
    (r.occurredAt.get.seconds, r.occurredAt.get.nanos) shouldBe occurredAt.toSecondsAndNanos
  }

  test("toBinary should encode Deleted correctly") {
    val id = DomainEventId.generate()
    val entityId = UserAccountId.generate()
    val occurredAt = DateTime.fromSecondsAndNanos(1740000000L, 333444555)

    val ev = UserAccountEvent.Deleted(id, entityId, occurredAt)
    val bytes = serializer.toBinary(ev)
    val proto = ProtoUserAccountEvent.parseFrom(bytes)

    proto.event.isDeleted shouldBe true
    val d = proto.getDeleted
    d.eventId shouldBe id.asString
    d.userAccountId shouldBe entityId.asString
    (d.occurredAt.get.seconds, d.occurredAt.get.nanos) shouldBe occurredAt.toSecondsAndNanos
  }

  test("fromBinary should fail on Empty event") {
    val empty = ProtoUserAccountEvent().toByteArray
    val ex = intercept[IllegalArgumentException] {
      serializer.fromBinary(empty, "")
    }
    ex.getMessage should include("Unexpected Empty event in UserAccountEvent")
  }
}

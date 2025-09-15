package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import io.github.j5ik2o.pcqrses.command.domain.basic.DateTime
import io.github.j5ik2o.pcqrses.command.domain.users.{
  EmailAddress,
  FirstName,
  LastName,
  UserAccount,
  UserAccountId,
  UserAccountName
}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.persistence.users.UserAccountSnapshot
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class UserAccountSnapshotSerializerSpec extends AnyFreeSpec with Matchers {

  import UserAccountAggregateState.{Created, Deleted, NotCreated}

  private val serializer = new UserAccountSnapshotSerializer

  "UserAccountSnapshotSerializer" - {

    "identifier should be constant" in {
      serializer.identifier shouldBe 20001
    }

    "manifest should return expected strings" in {
      val id = UserAccountId.generate()
      val (user, _) = UserAccount(
        id,
        UserAccountName(FirstName("John"), LastName("Doe")),
        EmailAddress("john.doe@example.com"),
        createdAt = DateTime.fromSecondsAndNanos(1710000000L, 123456789),
        updatedAt = DateTime.fromSecondsAndNanos(1710001111L, 987654321)
      )
      serializer.manifest(NotCreated(id)) shouldBe "NotCreated"
      serializer.manifest(Created(user)) shouldBe "Created"
      serializer.manifest(Deleted(user)) shouldBe "Deleted"
    }

    "toBinary should encode NotCreated correctly" in {
      val id = UserAccountId.generate()
      val state = NotCreated(id)
      val bytes = serializer.toBinary(state)
      val snapshot = UserAccountSnapshot.parseFrom(bytes)
      snapshot.state.isNotCreated shouldBe true
      snapshot.getNotCreated.userAccountId shouldBe id.asString
    }

    "toBinary should encode Created correctly" in {
      val id = UserAccountId.generate()
      val name = UserAccountName(FirstName("Alice"), LastName("Smith"))
      val email = EmailAddress("alice.smith@example.com")
      val createdAt = DateTime.fromSecondsAndNanos(1720000000L, 111222333)
      val updatedAt = DateTime.fromSecondsAndNanos(1720001234L, 444555666)
      val (user, _) = UserAccount(id, name, email, createdAt = createdAt, updatedAt = updatedAt)

      val state = Created(user)
      val bytes = serializer.toBinary(state)
      val snapshot = UserAccountSnapshot.parseFrom(bytes)
      snapshot.state.isCreated shouldBe true
      val s = snapshot.getCreated
      s.userAccountId shouldBe id.asString
      s.userName.get.firstName shouldBe name.breachEncapsulationOfFirstName.asString
      s.userName.get.lastName shouldBe name.breachEncapsulationOfLastName.asString
      s.emailAddress shouldBe email.asString
      (s.createdAt.get.seconds, s.createdAt.get.nanos) shouldBe createdAt.toSecondsAndNanos
      (s.updatedAt.get.seconds, s.updatedAt.get.nanos) shouldBe updatedAt.toSecondsAndNanos
    }

    "toBinary should encode Deleted correctly" in {
      val id = UserAccountId.generate()
      val name = UserAccountName(FirstName("Bob"), LastName("Johnson"))
      val email = EmailAddress("bob.johnson@example.com")
      val createdAt = DateTime.fromSecondsAndNanos(1730000000L, 101010101)
      val updatedAt = DateTime.fromSecondsAndNanos(1730002222L, 202020202)
      val (user0, _) = UserAccount(id, name, email, createdAt = createdAt, updatedAt = updatedAt)
      val deletedUser = user0.delete match {
        case Right((u, _)) => u
        case Left(err) => fail(s"Failed to delete user in test setup: $err")
      }

      val state = Deleted(deletedUser)
      val bytes = serializer.toBinary(state)
      val snapshot = UserAccountSnapshot.parseFrom(bytes)
      snapshot.state.isDeleted shouldBe true
      val s = snapshot.getDeleted
      s.userAccountId shouldBe id.asString
      s.userName.get.firstName shouldBe name.breachEncapsulationOfFirstName.asString
      s.userName.get.lastName shouldBe name.breachEncapsulationOfLastName.asString
      s.emailAddress shouldBe email.asString
      (s.createdAt.get.seconds, s.createdAt.get.nanos) shouldBe createdAt.toSecondsAndNanos
      val expectedUpdatedAt = deletedUser.updatedAt.toSecondsAndNanos
      (s.updatedAt.get.seconds, s.updatedAt.get.nanos) shouldBe expectedUpdatedAt
    }

    "fromBinary should fail on Empty state" in {
      val emptyBytes = UserAccountSnapshot().toByteArray // oneof state is not set
      val ex = intercept[IllegalArgumentException] {
        serializer.fromBinary(emptyBytes, "")
      }
      ex.getMessage should include ("Unexpected empty state in UserAccountSnapshot")
    }
  }
}

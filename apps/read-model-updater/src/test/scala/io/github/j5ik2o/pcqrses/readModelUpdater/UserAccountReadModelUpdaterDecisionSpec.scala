package io.github.j5ik2o.pcqrses.readModelUpdater

import io.github.j5ik2o.pcqrses.readModelUpdater.UserAccountReadModelUpdater.ApplyDecision
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class UserAccountReadModelUpdaterDecisionSpec extends AnyFreeSpec with Matchers {

  private def event(sequenceNumber: Long): JournalHeadEvent =
    JournalHeadEvent(sequenceNumber, Array.emptyByteArray)

  "decide" - {

    "applies only the events after the checkpoint in sequence order" in {
      val first = event(1L)
      val second = event(2L)
      val third = event(3L)
      val record = JournalHeadRecord("UserAccount-1", 3L, Vector(third, first, second))

      UserAccountReadModelUpdater.decide(record, 1L) shouldBe ApplyDecision.Apply(
        Vector(second, third)
      )
    }

    "does nothing when every event is at or below the checkpoint" in {
      val record = JournalHeadRecord("UserAccount-1", 2L, Vector(event(1L), event(2L)))

      UserAccountReadModelUpdater.decide(record, 2L) shouldBe ApplyDecision.NothingToApply
    }

    "fails a migration record whose head is ahead of the checkpoint" in {
      val record = JournalHeadRecord("UserAccount-1", 5L, Vector.empty)

      UserAccountReadModelUpdater.decide(record, 4L) shouldBe
        ApplyDecision.MigrationRecordAhead(checkpoint = 4L, head = 5L)
    }

    "skips a migration record whose head is at the checkpoint" in {
      val record = JournalHeadRecord("UserAccount-1", 5L, Vector.empty)

      UserAccountReadModelUpdater.decide(record, 5L) shouldBe
        ApplyDecision.SkipMigrationRecord
    }

    "skips a migration record whose head is behind the checkpoint" in {
      val record = JournalHeadRecord("UserAccount-1", 5L, Vector.empty)

      UserAccountReadModelUpdater.decide(record, 6L) shouldBe
        ApplyDecision.SkipMigrationRecord
    }
  }

  "validateRenamedRows" - {

    "fails when the rename updated no rows" in {
      UserAccountReadModelUpdater.validateRenamedRows("UserAccount-1", 0).isLeft shouldBe true
    }

    "succeeds when the rename updated a row" in {
      UserAccountReadModelUpdater.validateRenamedRows("UserAccount-1", 1).isRight shouldBe true
    }
  }
}

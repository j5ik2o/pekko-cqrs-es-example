package io.github.j5ik2o.pcqrses.readModelUpdater

import io.github.j5ik2o.pcqrses.readModelUpdater.UserAccountReadModelUpdater.ApplyDecision
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

class ReadModelUpdatePipelineSpec extends AnyFreeSpec with Matchers {

  private val event = JournalHeadEvent(2L, Array.emptyByteArray)
  private val record = JournalHeadRecord("UserAccount-01JTEST", 2L, Vector(event))

  /** 更新と通知の呼び出しを順に記録する。 */
  private final class Recorder(
    applyResult: Either[Throwable, ApplyDecision],
    notifyResult: Either[Throwable, Unit] = Right(())) {
    val calls: ListBuffer[String] = ListBuffer.empty

    val pipeline = new ReadModelUpdatePipeline(
      applyRecord = r => {
        calls += s"apply:${r.persistenceId}"
        applyResult
      },
      notifier = (notification: ReadModelUpdated) => {
        calls += s"notify:${notification.persistenceId}:${notification.sequenceNr}"
        notifyResult
      }
    )
  }

  "process" - {

    "notifies after the read model update is committed" in {
      val recorder = new Recorder(Right(ApplyDecision.Apply(Vector(event))))

      recorder.pipeline.process(record) shouldBe Right(())
      recorder.calls.toList shouldBe List(
        "apply:UserAccount-01JTEST",
        "notify:UserAccount-01JTEST:2"
      )
    }

    "does not notify when the update fails" in {
      val error = new IllegalStateException("db down")
      val recorder = new Recorder(Left(error))

      recorder.pipeline.process(record) shouldBe Left(ReadModelUpdatePipeline.Failure.Apply(error))
      recorder.calls.toList shouldBe List("apply:UserAccount-01JTEST")
    }

    "fails the record when the notification fails, so that the caller retries it" in {
      val error = new RuntimeException("sns down")
      val recorder = new Recorder(Right(ApplyDecision.Apply(Vector(event))), Left(error))

      recorder.pipeline.process(record) shouldBe Left(ReadModelUpdatePipeline.Failure.Notify(error))
    }

    "notifies again on a retried record that was already applied" in {
      val recorder = new Recorder(Right(ApplyDecision.NothingToApply))

      recorder.pipeline.process(record) shouldBe Right(())
      recorder.calls.toList shouldBe List(
        "apply:UserAccount-01JTEST",
        "notify:UserAccount-01JTEST:2"
      )
    }

    "does not notify a migration record" in {
      val migration = JournalHeadRecord("UserAccount-01JTEST", 5L, Vector.empty)
      val recorder = new Recorder(Right(ApplyDecision.SkipMigrationRecord))

      recorder.pipeline.process(migration) shouldBe Right(())
      recorder.calls.toList shouldBe List("apply:UserAccount-01JTEST")
    }
  }

  "ReadModelUpdated.toJson" - {
    "carries the persistence id and the sequence number" in {
      ReadModelUpdated.toJson(ReadModelUpdated("UserAccount-01JTEST", 2L)) shouldBe
        """{"type":"ReadModelUpdated","persistenceId":"UserAccount-01JTEST","sequenceNr":2}"""
    }
  }
}

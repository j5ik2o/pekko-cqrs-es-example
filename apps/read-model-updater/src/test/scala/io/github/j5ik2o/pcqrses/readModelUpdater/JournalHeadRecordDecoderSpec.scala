package io.github.j5ik2o.pcqrses.readModelUpdater

import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

class JournalHeadRecordDecoderSpec extends AnyFreeSpec with Matchers {

  private val columns = JournalHeadColumns(
    persistenceId = "persistence-id",
    sequenceNr = "sequence-nr",
    events = "events",
    eventSequenceNr = "sequence-nr",
    eventMessage = "message"
  )

  private def string(value: String): AttributeValue = new AttributeValue().withS(value)

  private def number(value: Long): AttributeValue = new AttributeValue().withN(value.toString)

  private def binary(value: String): AttributeValue =
    new AttributeValue().withB(ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8)))

  private def event(sequenceNumber: Long, message: String): AttributeValue =
    new AttributeValue().withM(
      Map(
        "sequence-nr" -> number(sequenceNumber),
        "message" -> binary(message)
      ).asJava
    )

  private def imageWithHead(
    headSequenceNumber: Long,
    events: AttributeValue*
  ): java.util.Map[String, AttributeValue] =
    Map(
      "persistence-id" -> string("UserAccount-01JTEST"),
      "sequence-nr" -> number(headSequenceNumber),
      "events" -> new AttributeValue().withL(events.asJava)
    ).asJava

  "JournalHeadRecordDecoder" - {

    "decodes a record with events in sequence order" in {
      val decoded = JournalHeadRecordDecoder.decode(
        imageWithHead(2L, event(1L, "first"), event(2L, "second")),
        columns
      )

      decoded.isRight shouldBe true
      val record = decoded.toOption.get
      record.persistenceId shouldBe "UserAccount-01JTEST"
      record.sequenceNumber shouldBe 2L
      record.events.map(_.sequenceNumber) shouldBe Vector(1L, 2L)
      record.events.head.message.toSeq shouldBe "first".getBytes(StandardCharsets.UTF_8).toSeq
      record.events.last.message.toSeq shouldBe "second".getBytes(StandardCharsets.UTF_8).toSeq
    }

    "decodes a migration record with no events" in {
      val decoded = JournalHeadRecordDecoder.decode(imageWithHead(5L), columns)

      decoded.isRight shouldBe true
      val record = decoded.toOption.get
      record.sequenceNumber shouldBe 5L
      record.events shouldBe empty
    }

    "rejects a missing persistence id" in {
      val image = Map(
        "sequence-nr" -> number(1L),
        "events" -> new AttributeValue().withL(java.util.List.of())
      ).asJava

      JournalHeadRecordDecoder.decode(image, columns) shouldBe Left(
        DecodeError("missing attribute: persistence-id"))
    }

    "rejects a non-numeric sequence number" in {
      val image = Map(
        "persistence-id" -> string("UserAccount-01JTEST"),
        "sequence-nr" -> new AttributeValue().withN("not-a-number"),
        "events" -> new AttributeValue().withL(java.util.List.of())
      ).asJava

      JournalHeadRecordDecoder.decode(image, columns) shouldBe
        Left(DecodeError("attribute sequence-nr is not a number"))
    }

    "rejects a missing events attribute" in {
      val image = Map(
        "persistence-id" -> string("UserAccount-01JTEST"),
        "sequence-nr" -> number(1L)
      ).asJava

      JournalHeadRecordDecoder.decode(image, columns) shouldBe Left(
        DecodeError("missing attribute: events"))
    }

    "rejects an event without a message" in {
      val image = imageWithHead(
        1L,
        new AttributeValue().withM(Map("sequence-nr" -> number(1L)).asJava)
      )

      JournalHeadRecordDecoder.decode(image, columns) shouldBe
        Left(DecodeError("missing attribute: message"))
    }
  }

  "JournalHeadRecord" - {

    "returns only the events above the checkpoint in sequence order" in {
      val record = JournalHeadRecord(
        "UserAccount-01JTEST",
        3L,
        Vector(
          JournalHeadEvent(3L, Array.emptyByteArray),
          JournalHeadEvent(1L, Array.emptyByteArray),
          JournalHeadEvent(2L, Array.emptyByteArray)
        )
      )

      record.eventsAfter(1L).map(_.sequenceNumber) shouldBe Vector(2L, 3L)
      record.eventsAfter(3L) shouldBe empty
    }
  }
}

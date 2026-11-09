package io.github.j5ik2o.pcqrses.readModelUpdater

import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue

import java.nio.ByteBuffer
import scala.jdk.CollectionConverters.*

final case class DecodeError(message: String)

/** DynamoDB Streams の NewImage を JournalHeadRecord に変換する。 */
object JournalHeadRecordDecoder {

  def decode(
    image: java.util.Map[String, AttributeValue],
    columns: JournalHeadColumns
  ): Either[DecodeError, JournalHeadRecord] =
    for {
      persistenceId <- stringAttribute(image, columns.persistenceId)
      sequenceNumber <- longAttribute(image, columns.sequenceNr)
      events <- listAttribute(image, columns.events).flatMap(decodeEvents(_, columns))
    } yield JournalHeadRecord(persistenceId, sequenceNumber, events)

  private def decodeEvents(
    events: java.util.List[AttributeValue],
    columns: JournalHeadColumns
  ): Either[DecodeError, Vector[JournalHeadEvent]] =
    events.asScala.foldLeft[Either[DecodeError, Vector[JournalHeadEvent]]](Right(Vector.empty)) {
      case (Right(acc), event) => decodeEvent(event, columns).map(acc :+ _)
      case (left, _) => left
    }

  private def decodeEvent(
    event: AttributeValue,
    columns: JournalHeadColumns
  ): Either[DecodeError, JournalHeadEvent] =
    for {
      values <- mapAttribute(event)
      sequenceNumber <- longAttribute(values, columns.eventSequenceNr)
      message <- binaryAttribute(values, columns.eventMessage)
    } yield JournalHeadEvent(sequenceNumber, message)

  private def mapAttribute(
    value: AttributeValue): Either[DecodeError, java.util.Map[String, AttributeValue]] =
    Option(value).flatMap(v => Option(v.getM)).toRight(DecodeError("event is not a map"))

  private def stringAttribute(
    values: java.util.Map[String, AttributeValue],
    name: String
  ): Either[DecodeError, String] =
    attribute(values, name).flatMap { value =>
      Option(value.getS).toRight(DecodeError(s"attribute $name is not a string"))
    }

  private def longAttribute(
    values: java.util.Map[String, AttributeValue],
    name: String
  ): Either[DecodeError, Long] =
    attribute(values, name).flatMap { value =>
      Option(value.getN)
        .flatMap(_.toLongOption)
        .toRight(DecodeError(s"attribute $name is not a number"))
    }

  private def binaryAttribute(
    values: java.util.Map[String, AttributeValue],
    name: String
  ): Either[DecodeError, Array[Byte]] =
    attribute(values, name).flatMap { value =>
      Option(value.getB).map(toBytes).toRight(DecodeError(s"attribute $name is not binary"))
    }

  private def listAttribute(
    values: java.util.Map[String, AttributeValue],
    name: String
  ): Either[DecodeError, java.util.List[AttributeValue]] =
    attribute(values, name).flatMap { value =>
      Option(value.getL).toRight(DecodeError(s"attribute $name is not a list"))
    }

  private def attribute(
    values: java.util.Map[String, AttributeValue],
    name: String
  ): Either[DecodeError, AttributeValue] =
    Option(values)
      .flatMap(v => Option(v.get(name)))
      .toRight(DecodeError(s"missing attribute: $name"))

  private def toBytes(buffer: ByteBuffer): Array[Byte] = {
    val duplicate = buffer.duplicate()
    val bytes = new Array[Byte](duplicate.remaining())
    duplicate.get(bytes)
    bytes
  }
}

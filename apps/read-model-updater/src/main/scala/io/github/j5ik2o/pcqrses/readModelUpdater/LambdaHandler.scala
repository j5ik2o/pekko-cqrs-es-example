package io.github.j5ik2o.pcqrses.readModelUpdater

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.SerializationExtension
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.jdk.CollectionConverters.*

/** JournalHead テーブルの DynamoDB Stream を消費し、Read Model を更新する Lambda。 */
class LambdaHandler extends RequestHandler[DynamodbEvent, java.util.Map[String, Object]] {

  private val logger = LoggerFactory.getLogger(getClass)

  private val config = ConfigFactory.load()
  private val updaterConfig = ReadModelUpdaterConfig.fromConfig(config)

  private val databaseOperationTimeout: FiniteDuration =
    Duration.fromNanos(config.getDuration("read-model-updater.timeouts.database-operation").toNanos)

  private val databaseConfig: DatabaseConfig[JdbcProfile] =
    DatabaseConfig.forConfig[JdbcProfile]("read-model-updater.slick", config)

  private lazy val system = ActorSystem("read-model-updater", config)
  private lazy val serialization = SerializationExtension(system)
  private lazy val updater =
    new UserAccountReadModelUpdater(databaseConfig, serialization, databaseOperationTimeout)

  override def handleRequest(
    input: DynamodbEvent,
    context: Context): java.util.Map[String, Object] = {
    val records = Option(input)
      .flatMap(event => Option(event.getRecords))
      .map(_.asScala.toVector)
      .getOrElse(Vector.empty)

    if (records.isEmpty) {
      logger.info("No DynamoDB stream records to process")
      return response(Vector.empty)
    }

    logger.info(s"Received ${records.size} records from the ${updaterConfig.headTableName} stream")

    // 同じ persistence id の後続レコードを失敗したレコードより先に適用しないよう、
    // 最初の失敗で打ち切り、そのレコード以降をまとめて再試行対象にする。
    val failures = Vector.newBuilder[String]
    var index = 0
    var failed = false
    while (index < records.size && !failed) {
      val record = records(index)
      processRecord(record) match {
        case Right(_) =>
        case Left(error) =>
          logger.error(
            s"Failed to process record ${itemIdentifier(record)}: ${error.message}",
            error.exception.orNull
          )
          failed = true
          records.drop(index).foreach(remaining => failures += itemIdentifier(remaining))
      }
      index += 1
    }
    response(failures.result())
  }

  private def processRecord(record: DynamodbStreamRecord): Either[ProcessingError, Unit] =
    try
      if (!isHeadTable(record)) {
        Right(())
      } else {
        Option(record.getDynamodb).flatMap(dynamodb => Option(dynamodb.getNewImage)) match {
          case None =>
            logger.debug(s"Skipping record without a new image: ${itemIdentifier(record)}")
            Right(())

          case Some(image) =>
            JournalHeadRecordDecoder.decode(image, updaterConfig.columns) match {
              case Left(error) =>
                Left(
                  ProcessingError(s"cannot decode the JournalHead record: ${error.message}", None))

              case Right(head)
                  if !head.persistenceId.startsWith(updaterConfig.persistenceIdPrefix) =>
                logger.debug(s"Skipping persistence id ${head.persistenceId}")
                Right(())

              case Right(head) =>
                updater.apply(head).left.map { error =>
                  ProcessingError(
                    s"cannot apply the JournalHead record: ${error.getMessage}",
                    Some(error))
                }
            }
        }
      }
    catch {
      case ex: Exception =>
        Left(ProcessingError(s"unexpected error: ${ex.getMessage}", Some(ex)))
    }

  private def isHeadTable(record: DynamodbStreamRecord): Boolean =
    Option(record.getEventSourceARN) match {
      case None => true
      case Some(arn) => arn.split("/").lift(1).contains(updaterConfig.headTableName)
    }

  private def itemIdentifier(record: DynamodbStreamRecord): String =
    Option(record.getDynamodb)
      .flatMap(dynamodb => Option(dynamodb.getSequenceNumber))
      .orElse(Option(record.getEventID))
      .getOrElse("unknown")

  private def response(failures: Vector[String]): java.util.Map[String, Object] = {
    val batchItemFailures = failures.map { identifier =>
      val failure = new java.util.LinkedHashMap[String, String]()
      failure.put("itemIdentifier", identifier)
      failure
    }.asJava

    val body = new java.util.LinkedHashMap[String, Object]()
    body.put("batchItemFailures", batchItemFailures)
    body
  }

  private final case class ProcessingError(message: String, exception: Option[Throwable])
}

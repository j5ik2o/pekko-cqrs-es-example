package io.github.j5ik2o.pcqrses.readModelUpdater

import io.github.j5ik2o.pcqrses.command.domain.users.UserAccountEvent
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao.UserAccountsComponent
import org.apache.pekko.persistence.PersistentRepr
import org.apache.pekko.serialization.Serialization
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import java.sql.Timestamp
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object UserAccountReadModelUpdater {

  sealed trait ApplyDecision

  object ApplyDecision {
    final case class Apply(events: Vector[JournalHeadEvent]) extends ApplyDecision
    case object NothingToApply extends ApplyDecision
    case object SkipMigrationRecord extends ApplyDecision
    final case class MigrationRecordAhead(checkpoint: Long, head: Long) extends ApplyDecision
  }

  /** checkpoint と JournalHead レコードから、適用するイベントを決める。 */
  def decide(record: JournalHeadRecord, checkpoint: Long): ApplyDecision =
    if (record.events.isEmpty) {
      if (checkpoint < record.sequenceNumber) {
        ApplyDecision.MigrationRecordAhead(checkpoint, record.sequenceNumber)
      } else {
        ApplyDecision.SkipMigrationRecord
      }
    } else {
      val pending = record.eventsAfter(checkpoint)
      if (pending.isEmpty) ApplyDecision.NothingToApply
      else ApplyDecision.Apply(pending)
    }

  /** Renamed_V1 が0行更新の場合は、Created_V1 を取り逃した可能性があるため失敗させる。 */
  def validateRenamedRows(entityId: String, updatedRows: Int): Either[Throwable, Unit] =
    if (updatedRows == 0) {
      Left(
        new IllegalStateException(
          s"Renamed_V1 for $entityId updated no rows; rebuild the read model from the journal"
        )
      )
    } else {
      Right(())
    }
}

/** JournalHead の1レコードを、チェックポイントと同じトランザクションで Read Model に適用する。 */
final class UserAccountReadModelUpdater(
  databaseConfig: DatabaseConfig[JdbcProfile],
  serialization: Serialization,
  databaseOperationTimeout: FiniteDuration
) {
  import UserAccountReadModelUpdater.*

  private val logger = LoggerFactory.getLogger(getClass)

  private val component = new UserAccountsComponent with ReadModelCheckpointsComponent {
    override val profile: JdbcProfile = databaseConfig.profile
  }
  import component.profile.api.*

  private val persistentReprSerializer = serialization.serializerFor(classOf[PersistentRepr])

  def apply(record: JournalHeadRecord): Either[Throwable, Unit] = {
    val action = for {
      _ <- component.ReadModelCheckpointsDao.ensureExists(record.persistenceId)
      checkpoint <- component.ReadModelCheckpointsDao.lockByPersistenceId(record.persistenceId)
      _ <- decide(record, checkpoint.sequenceNr) match {
        case ApplyDecision.Apply(events) =>
          applyEvents(record, events)

        case ApplyDecision.NothingToApply =>
          DBIO.successful(())

        case ApplyDecision.SkipMigrationRecord =>
          logger.info(
            s"Skipping migration record for ${record.persistenceId}: checkpoint ${checkpoint.sequenceNr} is at or ahead of head ${record.sequenceNumber}"
          )
          DBIO.successful(())

        case ApplyDecision.MigrationRecordAhead(checkpointSequenceNr, headSequenceNr) =>
          DBIO.failed(
            new IllegalStateException(
              s"JournalHead record for ${record.persistenceId} has no events, but checkpoint $checkpointSequenceNr is behind head $headSequenceNr; rebuild the read model from the journal before continuing"
            )
          )
      }
    } yield ()
    Try(
      Await
        .result(databaseConfig.db.run(action.transactionally), databaseOperationTimeout)).toEither
  }

  private def applyEvents(
    record: JournalHeadRecord,
    pendingEvents: Vector[JournalHeadEvent]
  ): DBIO[Unit] =
    decodeEvents(pendingEvents) match {
      case Left(error) =>
        DBIO.failed(error)

      case Right(payloads) =>
        val pending = pendingEvents.flatMap { event =>
          payloads.get(event.sequenceNumber).map(event -> _)
        }
        for {
          _ <- pending.foldLeft[DBIO[Unit]](DBIO.successful(())) { case (acc, (_, event)) =>
            acc.flatMap(_ => applyEvent(event))
          }
          _ <- pending.lastOption match {
            case Some((event, _)) =>
              component.ReadModelCheckpointsDao
                .save(
                  component.ReadModelCheckpoint(
                    record.persistenceId,
                    event.sequenceNumber,
                    new Timestamp(System.currentTimeMillis())
                  )
                )
                .map(_ => ())
            case None => DBIO.successful(())
          }
        } yield ()
    }

  private def decodeEvents(
    events: Vector[JournalHeadEvent]
  ): Either[Throwable, Map[Long, UserAccountEvent]] =
    events
      .sortBy(_.sequenceNumber)
      .foldLeft[Either[Throwable, Map[Long, UserAccountEvent]]](Right(Map.empty)) {
        case (Right(acc), event) =>
          deserialize(event.message).map(payload => acc.updated(event.sequenceNumber, payload))
        case (left, _) => left
      }

  private def deserialize(message: Array[Byte]): Either[Throwable, UserAccountEvent] =
    Try(
      persistentReprSerializer
        .fromBinary(message, classOf[PersistentRepr])
        .asInstanceOf[PersistentRepr]).flatMap { persistentRepr =>
      persistentRepr.payload match {
        case event: UserAccountEvent => Success(event)
        case other =>
          Failure(
            new IllegalArgumentException(s"unexpected payload type: ${other.getClass.getName}"))
      }
    }.toEither

  private def applyEvent(event: UserAccountEvent): DBIO[Unit] =
    event match {
      case UserAccountEvent.Created_V1(_, entityId, name, _, occurredAt) =>
        val occurred = Timestamp.from(occurredAt.asInstant())
        component.UserAccountsDao
          .insertOrUpdate(
            component.UserAccountsRecord(
              id = entityId.asString,
              firstName = name.breachEncapsulationOfFirstName.asString,
              lastName = name.breachEncapsulationOfLastName.asString,
              createdAt = occurred,
              updatedAt = occurred
            )
          )
          .map(_ => ())

      case UserAccountEvent.Renamed_V1(_, entityId, _, newName, occurredAt) =>
        component.UserAccountsDao
          .filter(_.id === entityId.asString)
          .map(r => (r.firstName, r.lastName, r.updatedAt))
          .update(
            (
              newName.breachEncapsulationOfFirstName.asString,
              newName.breachEncapsulationOfLastName.asString,
              Timestamp.from(occurredAt.asInstant())
            )
          )
          .flatMap { updatedRows =>
            validateRenamedRows(entityId.asString, updatedRows) match {
              case Left(error) => DBIO.failed(error)
              case Right(_) => DBIO.successful(())
            }
          }

      case UserAccountEvent.Deleted_V1(_, entityId, _) =>
        component.UserAccountsDao.filter(_.id === entityId.asString).delete.map(_ => ())
    }
}

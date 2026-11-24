package io.github.j5ik2o.pcqrses.readModelUpdater

import com.typesafe.config.Config

/** JournalHead テーブルのカラム名。ライブラリの head.columns-def と同じ既定値を持つ。 */
final case class JournalHeadColumns(
  persistenceId: String,
  sequenceNr: String,
  events: String,
  eventSequenceNr: String,
  eventMessage: String
)

object JournalHeadColumns {

  def fromConfig(config: Config): JournalHeadColumns = {
    val columns = config.getConfig("read-model-updater.stream.columns")
    JournalHeadColumns(
      persistenceId = columns.getString("persistence-id"),
      sequenceNr = columns.getString("sequence-nr"),
      events = columns.getString("events"),
      eventSequenceNr = columns.getString("event-sequence-nr"),
      eventMessage = columns.getString("event-message")
    )
  }
}

/** SNS トピック。SNS の Publish はトピックと同じリージョンの接続先でしか受け付けないので、ARN のリージョンを持つ。 */
final case class SnsTopic(arn: String, region: String)

object SnsTopic {

  /** `arn:<partition>:sns:<region>:<account>:<name>` を読む。 */
  def parse(arn: String): Either[String, SnsTopic] =
    arn.split(":", -1).toList match {
      case "arn" :: partition :: "sns" :: region :: account :: name :: Nil
          if Seq(partition, region, account, name).forall(_.nonEmpty) =>
        Right(SnsTopic(arn, region))
      case _ =>
        Left(s"not an SNS topic ARN: $arn")
    }
}

/** Read Model 更新後の通知先。topic-arn が空なら通知しない。 */
final case class NotificationConfig(topic: Option[SnsTopic], endpoint: Option[String])

object NotificationConfig {

  def fromConfig(config: Config): NotificationConfig = {
    val notification = config.getConfig("read-model-updater.notification")
    def optional(path: String): Option[String] =
      Some(notification.getString(path).trim).filter(_.nonEmpty)
    val topic = optional("topic-arn").map { arn =>
      SnsTopic.parse(arn).fold(message => throw new IllegalArgumentException(message), identity)
    }
    NotificationConfig(topic = topic, endpoint = optional("endpoint"))
  }
}

final case class ReadModelUpdaterConfig(
  headTableName: String,
  persistenceIdPrefix: String,
  columns: JournalHeadColumns,
  notification: NotificationConfig
)

object ReadModelUpdaterConfig {

  def fromConfig(config: Config): ReadModelUpdaterConfig = {
    val stream = config.getConfig("read-model-updater.stream")
    ReadModelUpdaterConfig(
      headTableName = stream.getString("head-table-name"),
      persistenceIdPrefix = stream.getString("persistence-id-prefix"),
      columns = JournalHeadColumns.fromConfig(config),
      notification = NotificationConfig.fromConfig(config)
    )
  }
}

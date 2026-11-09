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

final case class ReadModelUpdaterConfig(
  headTableName: String,
  persistenceIdPrefix: String,
  columns: JournalHeadColumns
)

object ReadModelUpdaterConfig {

  def fromConfig(config: Config): ReadModelUpdaterConfig = {
    val stream = config.getConfig("read-model-updater.stream")
    ReadModelUpdaterConfig(
      headTableName = stream.getString("head-table-name"),
      persistenceIdPrefix = stream.getString("persistence-id-prefix"),
      columns = JournalHeadColumns.fromConfig(config)
    )
  }
}

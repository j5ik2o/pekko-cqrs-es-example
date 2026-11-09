package io.github.j5ik2o.pcqrses.readModelUpdater

/** JournalHead テーブルの1イベント。message はジャーナル行と同じ PersistentRepr のバイト列。 */
final case class JournalHeadEvent(sequenceNumber: Long, message: Array[Byte])

/** JournalHead テーブルの1アイテム。events はその書き込みで発生したイベントだけを含む。 */
final case class JournalHeadRecord(
  persistenceId: String,
  sequenceNumber: Long,
  events: Vector[JournalHeadEvent]
) {

  /** checkpoint より後のイベントだけをシーケンス番号順に返す。 */
  def eventsAfter(checkpoint: Long): Vector[JournalHeadEvent] =
    events.filter(_.sequenceNumber > checkpoint).sortBy(_.sequenceNumber)
}

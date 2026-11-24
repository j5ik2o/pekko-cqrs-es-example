package io.github.j5ik2o.pcqrses.readModelUpdater

import io.github.j5ik2o.pcqrses.readModelUpdater.UserAccountReadModelUpdater.ApplyDecision

/**
 * JournalHead の1レコードについて、Read Model の更新と通知をこの順に行う。
 *
 * 通知は更新のトランザクションが確定してから送る。通知を受けたクライアントがリードAPIを叩いたとき、 Read Model がすでに更新されているようにするため。
 * 更新か通知のどちらかが失敗したら、そのレコードは失敗として返し、呼び出し側が再試行する。読み飛ばしはしない。
 */
final class ReadModelUpdatePipeline(
  applyRecord: JournalHeadRecord => Either[Throwable, ApplyDecision],
  notifier: ReadModelUpdateNotifier
) {
  import ReadModelUpdatePipeline.*

  def process(record: JournalHeadRecord): Either[Failure, Unit] =
    for {
      decision <- applyRecord(record).left.map(Failure.Apply.apply)
      _ <- notificationFor(record, decision) match {
        case None => Right(())
        case Some(notification) => notifier.publish(notification).left.map(Failure.Notify.apply)
      }
    } yield ()
}

object ReadModelUpdatePipeline {

  enum Failure {
    case Apply(cause: Throwable)
    case Notify(cause: Throwable)
  }

  /**
   * 更新後の Read Model がこのレコードを反映しているときに通知を返す。
   *
   * すでに適用済み（NothingToApply）でも通知する。通知の送信に失敗して再試行されたレコードは、checkpoint が進んでいるので NothingToApply
   * になる。ここで通知しないと、その通知は失われる。移行のレコードは Read Model を変えないので通知しない。
   */
  def notificationFor(
    record: JournalHeadRecord,
    decision: ApplyDecision): Option[ReadModelUpdated] =
    decision match {
      case ApplyDecision.Apply(_) | ApplyDecision.NothingToApply =>
        Some(ReadModelUpdated(record.persistenceId, record.sequenceNumber))
      case ApplyDecision.SkipMigrationRecord | ApplyDecision.MigrationRecordAhead(_, _) =>
        None
    }
}

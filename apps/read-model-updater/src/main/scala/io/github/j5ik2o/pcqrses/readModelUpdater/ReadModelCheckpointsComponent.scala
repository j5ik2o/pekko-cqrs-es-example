package io.github.j5ik2o.pcqrses.readModelUpdater

import io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao.SlickDaoSupport
import slick.lifted.ProvenShape

import java.sql.Timestamp

/** Read Model の適用済みシーケンス番号を persistence id ごとに保持する。 */
trait ReadModelCheckpointsComponent extends SlickDaoSupport {
  import profile.api.*

  final case class ReadModelCheckpoint(
    persistenceId: String,
    sequenceNr: Long,
    updatedAt: Timestamp
  )

  final class ReadModelCheckpoints(tag: Tag)
    extends TableBase[ReadModelCheckpoint](tag, "read_model_checkpoints") {
    def persistenceId: Rep[String] = column[String]("persistence_id", O.PrimaryKey)
    def sequenceNr: Rep[Long] = column[Long]("sequence_nr")
    def updatedAt: Rep[Timestamp] = column[Timestamp]("updated_at")

    override def * : ProvenShape[ReadModelCheckpoint] =
      (persistenceId, sequenceNr, updatedAt) <> (
        ReadModelCheckpoint.apply,
        ReadModelCheckpoint.unapply)
  }

  object ReadModelCheckpointsDao extends TableQuery(tag => new ReadModelCheckpoints(tag)) {

    /** checkpoint 行がなければ 0 で作成する。同時実行時は unique 制約で直列化する。 */
    def ensureExists(persistenceId: String): DBIO[Int] =
      sqlu"insert into read_model_checkpoints (persistence_id, sequence_nr) values ($persistenceId, 0) on conflict (persistence_id) do nothing"

    /** 同じ persistence id の更新を直列化するため、トランザクション内で行ロックを取る。 */
    def lockByPersistenceId(persistenceId: String): DBIO[ReadModelCheckpoint] =
      filter(_.persistenceId === persistenceId).forUpdate.result.head

    def save(checkpoint: ReadModelCheckpoint): DBIO[Int] =
      ReadModelCheckpointsDao.insertOrUpdate(checkpoint)
  }
}

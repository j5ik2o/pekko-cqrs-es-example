package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql

import slick.jdbc.JdbcProfile
import slick.dbio.DBIO

import scala.concurrent.{ExecutionContext, Future}

/**
 * GraphQLリゾルバー用のコンテキスト
 *
 * データベース操作の実行機能とその他の共通リソースを提供する。 DIコンテナを使わずにSlickのDBアクセスを抽象化する役割を持つ。
 */
final case class ResolverContext(
  private val dbRunner: DBIO[?] => Future[?],
  private val executionContext: ExecutionContext
) {
  implicit val ec: ExecutionContext = executionContext

  /**
   * DBIOアクションを実行する
   *
   * @param action
   *   実行するDBIOアクション
   * @return
   *   実行結果のFuture
   */
  def runDbAction[T](action: DBIO[T]): Future[T] =
    dbRunner(action).asInstanceOf[Future[T]]

  /**
   * トランザクション内でDBIOアクションを実行する
   *
   * @param action
   *   実行するDBIOアクション
   * @return
   *   実行結果のFuture
   */
  def runDbActionTransactionally[T](action: DBIO[T])(implicit profile: JdbcProfile): Future[T] = {
    import profile.api._
    runDbAction(action.transactionally)
  }
}

object ResolverContext {

  /**
   * Slickのデータベース接続からResolverContextを生成
   *
   * @param db
   *   Slickのデータベース接続
   * @param ec
   *   ExecutionContext
   * @return
   *   ResolverContextインスタンス
   */
  def fromSlickDatabase(db: JdbcProfile#Backend#Database)(implicit
    ec: ExecutionContext): ResolverContext =
    ResolverContext(
      dbRunner = action => db.run(action.asInstanceOf[DBIO[Any]]),
      executionContext = ec
    )

  /**
   * テスト用のモックコンテキストを作成
   *
   * @param mockRunner
   *   モック用のDBランナー関数
   * @param ec
   *   ExecutionContext
   * @return
   *   ResolverContextインスタンス
   */
  def forTesting(mockRunner: DBIO[?] => Future[?])(implicit ec: ExecutionContext): ResolverContext =
    ResolverContext(mockRunner, ec)
}

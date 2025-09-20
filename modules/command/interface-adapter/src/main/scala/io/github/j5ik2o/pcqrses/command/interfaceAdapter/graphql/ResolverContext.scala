package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql

import io.github.j5ik2o.pcqrses.command.useCase.users.UserAccountUseCase
import zio.{Runtime, Task}

import scala.concurrent.{ExecutionContext, Future}

/**
 * GraphQLリゾルバー用のコンテキスト
 *
 * @param userAccountUseCase
 *   ユーザーアカウントのユースケース
 * @param zioRuntime
 *   ZIOランタイム
 * @param ec
 *   ExecutionContext
 */
case class ResolverContext(
  userAccountUseCase: UserAccountUseCase,
  zioRuntime: Runtime[Any]
)(implicit ec: ExecutionContext) {

  /**
   * ZIO Taskを実行してFutureに変換
   */
  def runZioTask[A](task: Task[A]): Future[A] = {
    import zio.Unsafe
    Unsafe.unsafe { implicit u =>
      zioRuntime.unsafe.runToFuture(task)
    }
  }
}

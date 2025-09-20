package io.github.j5ik2o.pcqrses.command.useCase.users

import io.github.j5ik2o.pcqrses.command.domain.users.{EmailAddress, UserAccountId, UserAccountName}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol
import org.apache.pekko.actor.typed.{ActorRef, Scheduler}
import org.apache.pekko.util.Timeout
import zio.IO

import scala.concurrent.ExecutionContext

/**
 * UserAccountユースケースのインターフェース
 *
 * ビジネスロジックのインターフェースを定義し、 テストでモック化可能にする
 */
trait UserAccountUseCase {

  /**
   * ユーザーアカウントを作成する
   *
   * @param userAccountName
   *   ユーザー名
   * @param emailAddress
   *   メールアドレス
   * @return
   *   作成されたユーザーアカウントのID
   */
  def createUserAccount(
    userAccountName: UserAccountName,
    emailAddress: EmailAddress
  ): IO[UserAccountUseCaseError, UserAccountId]
}

object UserAccountUseCase {

  /**
   * UserAccountUseCaseのインスタンスを作成する
   *
   * @param userAccountAggregateRef
   *   ユーザーアカウントアグリゲートのActorRef
   * @param timeout
   *   アクタータイムアウト
   * @param scheduler
   *   スケジューラー
   * @param ec
   *   実行コンテキスト
   * @return
   *   UserAccountUseCaseのインスタンス
   */
  def apply(
    userAccountAggregateRef: ActorRef[UserAccountProtocol.Command]
  )(implicit
    timeout: Timeout,
    scheduler: Scheduler,
    ec: ExecutionContext
  ): UserAccountUseCase =
    new UserAccountUseCaseImpl(userAccountAggregateRef)
}

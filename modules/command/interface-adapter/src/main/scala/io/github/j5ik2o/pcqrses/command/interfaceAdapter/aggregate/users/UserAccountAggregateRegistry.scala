package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import io.github.j5ik2o.pcqrses.command.domain.users.UserAccountId
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.registry.GenericAggregateRegistry
import org.apache.pekko.actor.typed.{ActorSystem, Behavior}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object UserAccountRegistry {
  /**
   * 停止用の特別なUserAccountId（ULIDのゼロ値を使用）
   */
  private val StopMessageId: UserAccountId = UserAccountId.from("00000000000000000000000000")

  /**
   * モードに応じたBehaviorを作成 呼び出し側がspawnのタイミングとアクター名を制御できる
   */
  def create(
              mode: GenericAggregateRegistry.Mode = GenericAggregateRegistry.Mode.LocalMode,
              idleTimeout: Option[FiniteDuration] = None,
              enablePassivation: Boolean = true
            )(implicit system: ActorSystem[?]): Behavior[UserAccountProtocol.Command] =
    GenericAggregateRegistry.create[UserAccountId, UserAccountProtocol.Command](
      aggregateName = UserAccountId.EntityTypeName,
      mode = mode,
      idleTimeout = idleTimeout,
      enablePassivation = enablePassivation
    )(
      nameF = _.asString,
      aggregateBehavior = UserAccountAggregate.apply,
      extractId = str => Try(UserAccountId.from(str)),
      createIdleMessage = id => UserAccountProtocol.Stop(id),
      stopMessageId = Some(StopMessageId)
    )

  /**
   * 設定から動作モードを決定
   */
  def modeFromConfig(system: ActorSystem[?]): GenericAggregateRegistry.Mode =
    GenericAggregateRegistry.modeFromConfig(system)
}

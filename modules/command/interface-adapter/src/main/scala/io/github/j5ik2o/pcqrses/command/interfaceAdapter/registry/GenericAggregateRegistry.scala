package io.github.j5ik2o.pcqrses.command.interfaceAdapter.registry

import io.github.j5ik2o.pcqrses.command.domain.support.EntityId
import org.apache.pekko.actor.typed.{ActorSystem, Behavior}

import scala.reflect.ClassTag
import scala.util.Try
import scala.concurrent.duration.FiniteDuration

/**
 * 汎用レジストリファサード
 *
 * 環境に応じてローカルモードまたはクラスターモードを選択し、 集約アクターへのアクセスを統一的に提供する。
 */
object GenericRegistry {

  /**
   * 動作モード
   */
  enum Mode {
    case LocalMode
    case ClusterMode
  }

  /**
   * モードに応じたBehaviorを作成 呼び出し側がspawnのタイミングとアクター名を制御できる
   *
   * @param aggregateName
   *   集約名（例: "StaffAggregate", "EmployeeAggregate"）
   * @param mode
   *   動作モード（LocalMode または ClusterMode）
   * @param nameF
   *   集約IDからアクター名を生成する関数（ローカルモード用）
   * @param aggregateBehavior
   *   集約IDからBehaviorを生成する関数
   * @param extractId
   *   文字列から集約IDを抽出する関数（クラスターモード用）
   * @param createIdleMessage
   *   アイドル時のメッセージを生成する関数（クラスターモード用）
   * @param system
   *   ActorSystem（クラスターモード用）
   * @tparam ID
   *   集約IDの型
   * @tparam CMD
   *   コマンドの型
   * @return
   *   レジストリのBehavior
   */
  def create[ID <: EntityId, CMD <: { def id: ID } : ClassTag](
    aggregateName: String,
    mode: Mode = Mode.LocalMode,
    idleTimeout: Option[FiniteDuration] = None,
    enablePassivation: Boolean = true
  )(
    nameF: ID => String,
    aggregateBehavior: ID => Behavior[CMD],
    extractId: String => Try[ID],
    createIdleMessage: ID => CMD,
    stopMessageId: Option[ID] = None
  )(implicit system: ActorSystem[?]): Behavior[CMD] =
    mode match {
      case Mode.LocalMode =>
        // ローカルモード：GenericLocalRegistryを使用
        GenericLocalAggregateRegistry.create[ID, CMD](s"$aggregateName-registry")(nameF)(aggregateBehavior)

      case Mode.ClusterMode =>
        // クラスターモード：GenericClusterRegistryを使用
        GenericClusterAggregateRegistry.create[ID, CMD](aggregateName)(
          extractId = extractId,
          createIdleMessage = createIdleMessage,
          stopMessageId = stopMessageId,
          idleTimeout = idleTimeout,
          enablePassivation = enablePassivation
        )(aggregateBehavior)
    }

  /**
   * 設定から動作モードを決定
   *
   * @param system
   *   ActorSystem
   * @return
   *   動作モード
   */
  def modeFromConfig(system: ActorSystem[?]): Mode = {
    val config = system.settings.config
    if (config.hasPath("pekko.cluster.enabled") &&
      config.getBoolean("pekko.cluster.enabled")) {
      Mode.ClusterMode
    } else {
      Mode.LocalMode
    }
  }
}

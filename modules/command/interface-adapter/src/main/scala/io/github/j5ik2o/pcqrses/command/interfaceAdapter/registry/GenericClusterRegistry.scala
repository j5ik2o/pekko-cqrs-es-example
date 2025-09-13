package io.github.j5ik2o.pcqrses.command.interfaceAdapter.registry

import io.github.j5ik2o.pcqrses.command.domain.support.EntityId
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  Entity,
  EntityContext,
  EntityTypeKey
}

import scala.concurrent.duration.*
import scala.reflect.ClassTag
import scala.reflect.Selectable.reflectiveSelectable
import scala.util.{Failure, Success, Try}

/**
 * クラスターモードで集約アクターを分散管理する汎用シャーディング設定
 *
 * このオブジェクトは以下の責務を持つ：
 *   - クラスターシャーディングの初期化と設定
 *   - 集約のライフサイクル管理（パッシベーション）
 *   - シャーディングプロキシによるメッセージルーティング
 */
object GenericClusterRegistry {

  /**
   * デフォルトのアイドルタイムアウト
   */
  final val DefaultIdleTimeout: FiniteDuration = 120.seconds

  /**
   * アイドルタイムアウトを無効化するための特別な値
   */
  final val NoIdleTimeout: FiniteDuration = Duration.Zero

  /**
   * デフォルトのシャード数
   */
  final val DefaultNumberOfShards: Int = 100

  /**
   * クラスターシャーディングを初期化
   *
   * @param aggregateName
   *   集約名（例: "StaffAggregate", "EmployeeAggregate"）
   * @param clusterSharding
   *   ClusterShardingインスタンス
   * @param aggregateBehavior
   *   集約IDからBehaviorを生成する関数
   * @param extractId
   *   文字列から集約IDを抽出する関数
   * @param createIdleMessage
   *   アイドル時のメッセージを生成する関数
   * @param numberOfShards
   *   シャード数（デフォルト: 100）
   * @param idleTimeout
   *   アイドルタイムアウト（デフォルト: 120秒）
   * @tparam ID
   *   集約IDの型
   * @tparam CMD
   *   コマンドの型
   * @return
   *   シャーディングエンベロープのActorRef
   */
  def init[ID <: EntityId, CMD <: { def id: ID } : ClassTag](
    aggregateName: String,
    clusterSharding: ClusterSharding,
    aggregateBehavior: ID => Behavior[CMD],
    extractId: String => Try[ID],
    createIdleMessage: ID => CMD,
    stopMessageId: Option[ID] = None,
    numberOfShards: Int = DefaultNumberOfShards,
    idleTimeout: FiniteDuration = DefaultIdleTimeout,
    enablePassivation: Boolean = true
  )(implicit system: ActorSystem[?]): ActorRef[ShardingEnvelope[CMD]] = {

    val typeKey = EntityTypeKey[CMD](aggregateName)

    /**
     * 集約の振る舞いを定義 子アクターとして集約アクターを生成し、メッセージを転送する
     */
    def aggregateWrapper(entityContext: EntityContext[CMD]): Behavior[CMD] =
      Behaviors.setup { context =>
        context.log.info(s"Starting $aggregateName aggregate: ${entityContext.entityId}")

        // 集約IDをパース
        val aggregateId = extractId(entityContext.entityId) match {
          case Success(id) => id
          case Failure(exception) =>
            throw new IllegalArgumentException(
              s"Invalid aggregate ID: ${entityContext.entityId}",
              exception
            )
        }

        // 集約アクターを子アクターとして生成
        val childRef = context.spawn(
          aggregateBehavior(aggregateId),
          s"$aggregateName-${entityContext.entityId}"
        )

        // アイドルタイムアウトとパッシベーションの設定
        if (idleTimeout > Duration.Zero && enablePassivation) {
          val timeoutMessage = createIdleMessage(aggregateId)
          context.setReceiveTimeout(idleTimeout, timeoutMessage)

          Behaviors.receiveMessage[CMD] { msg =>
            if (msg == timeoutMessage) {
              // タイムアウト時はパッシベーションを開始
              context.log.debug(s"Aggregate ${entityContext.entityId} is idle, passivating...")
              entityContext.shard ! ClusterSharding.Passivate(context.self)
              Behaviors.same
            } else {
              childRef ! msg
              Behaviors.same
            }
          }
        } else {
          // タイムアウトまたはパッシベーションが無効な場合
          if (!enablePassivation) {
            context.log.debug(s"Passivation disabled for $aggregateName aggregate")
          } else {
            context.log.debug(s"Idle timeout disabled for $aggregateName aggregate")
          }
          Behaviors.receiveMessage[CMD] { msg =>
            childRef ! msg
            Behaviors.same
          }
        }
      }

    // エンティティを初期化
    val entity = Entity(typeKey)(aggregateWrapper)
      .withMessageExtractor(
        new GenericShardingMessageExtractor[ID, CMD](numberOfShards)
      )
      .withStopMessage(
        createIdleMessage(
          stopMessageId.getOrElse(
            // デフォルトの停止用IDを生成（ULIDのゼロ値）
            extractId("00000000000000000000000000").getOrElse(
              throw new IllegalArgumentException("Failed to create stop message ID")
            )
          )
        )
      )

    clusterSharding.init(entity)
  }

  /**
   * プロキシBehaviorを作成 クラスターシャーディングへメッセージを転送する
   *
   * @param aggregateName
   *   集約名
   * @param clusterSharding
   *   ClusterShardingインスタンス
   * @tparam ID
   *   集約IDの型
   * @tparam CMD
   *   コマンドの型
   * @return
   *   プロキシBehavior
   */
  def ofProxy[ID <: EntityId, CMD <: { def id: ID } : ClassTag](
    aggregateName: String,
    clusterSharding: ClusterSharding
  ): Behavior[CMD] =
    Behaviors.setup { context =>
      context.log.info(s"Starting $aggregateName sharding proxy")

      val typeKey = EntityTypeKey[CMD](aggregateName)

      Behaviors.receiveMessage { msg =>
        val entityRef = clusterSharding.entityRefFor(typeKey, msg.id.asString)
        entityRef ! msg
        Behaviors.same
      }
    }

  /**
   * 特定の集約への参照を取得
   *
   * @param aggregateName
   *   集約名
   * @param clusterSharding
   *   ClusterShardingインスタンス
   * @param aggregateId
   *   集約ID
   * @tparam ID
   *   集約IDの型
   * @tparam CMD
   *   コマンドの型
   * @return
   *   集約への参照
   */
  def entityRefFor[ID <: EntityId, CMD <: { def id: ID } : ClassTag](
    aggregateName: String,
    clusterSharding: ClusterSharding,
    aggregateId: ID
  ): org.apache.pekko.cluster.sharding.typed.scaladsl.EntityRef[CMD] = {
    val typeKey = EntityTypeKey[CMD](aggregateName)
    clusterSharding.entityRefFor(typeKey, aggregateId.asString)
  }
}

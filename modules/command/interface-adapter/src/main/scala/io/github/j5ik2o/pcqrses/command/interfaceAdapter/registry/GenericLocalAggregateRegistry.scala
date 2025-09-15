package io.github.j5ik2o.pcqrses.command.interfaceAdapter.registry

import io.github.j5ik2o.pcqrses.command.domain.support.EntityId
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.reflect.Selectable.reflectiveSelectable

/**
 * ローカルモードで集約アクターを管理する汎用レジストリ
 *
 * 各集約IDに対応するアクターへのルーティングを行う。 集約アクターは遅延作成され、必要に応じてspawnされる。
 */
object GenericLocalAggregateRegistry {

  /**
   * ローカルレジストリのBehaviorを作成
   *
   * @param name
   *   レジストリの名前（例: "staff-aggregates", "employee-aggregates"）
   * @param nameF
   *   集約IDからアクター名を生成する関数
   * @param childBehavior
   *   集約IDからBehaviorを生成する関数
   * @tparam ID
   *   集約IDの型
   * @tparam CMD
   *   コマンドの型
   * @return
   *   レジストリのBehavior
   */
  def create[ID <: EntityId, CMD <: { def id: ID }](
    name: String
  )(
    nameF: ID => String
  )(
    childBehavior: ID => Behavior[CMD]
  ): Behavior[CMD] =
    Behaviors.setup { context =>
      context.log.info(s"Starting local registry: $name")

      /**
       * 集約アクターを取得または作成
       *
       * @param aggregateId
       *   集約ID
       * @return
       *   集約アクターのActorRef
       */
      def getOrCreateRef(aggregateId: ID): ActorRef[CMD] = {
        val actorName = nameF(aggregateId)
        context.child(actorName) match {
          case Some(ref) =>
            context.log.debug(s"Found existing actor: $actorName")
            ref.unsafeUpcast[CMD]
          case None =>
            context.log.info(
              s"Creating new actor: $actorName for aggregate: ${aggregateId.asString}")
            context.spawn(childBehavior(aggregateId), actorName)
        }
      }

      Behaviors.receiveMessage { msg =>
        val aggregateId = msg.id.asInstanceOf[ID]
        val actorRef = getOrCreateRef(aggregateId)
        context.log.debug(s"Routing message to aggregate: ${aggregateId.asString}")
        actorRef ! msg
        Behaviors.same
      }
    }
}

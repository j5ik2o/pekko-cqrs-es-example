package io.github.j5ik2o.pcqrses.command.interfaceAdapter.registry

import com.typesafe.config.{Config, ConfigFactory}
import io.github.j5ik2o.pcqrses.command.domain.users.UserAccountId
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.test.ActorSpec
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.{Cluster, MemberStatus}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.duration.*
import scala.util.Try

object GenericClusterAggregateRegistrySpec {
  val config: Config = ConfigFactory
    .parseString(
      """
        |pekko {
        |  actor.provider = cluster
        |  remote.artery.canonical {
        |    hostname = "127.0.0.1"
        |    port = 0
        |  }
        |  cluster.sharding.passivation.strategy = none
        |}
        |""".stripMargin
    )
    .withFallback(ConfigFactory.load())

  sealed trait Command { def id: UserAccountId }
  final case class Ping(id: UserAccountId, replyTo: ActorRef[Pong]) extends Command
  final case class Fail(id: UserAccountId) extends Command
  final case class Stop(id: UserAccountId) extends Command
  final case class Pong(incarnation: UUID)

  def aggregate(id: UserAccountId): Behavior[Command] = Behaviors.setup { _ =>
    val incarnation = UUID.randomUUID()
    Behaviors.receiveMessage {
      case Ping(_, replyTo) =>
        replyTo ! Pong(incarnation)
        Behaviors.same
      case Fail(_) =>
        throw new IllegalArgumentException("injected precondition violation")
      case Stop(_) =>
        Behaviors.stopped
    }
  }
}

class GenericClusterAggregateRegistrySpec
  extends ActorSpec(GenericClusterAggregateRegistrySpec.config)
  with Matchers
  with Eventually {
  import GenericClusterAggregateRegistrySpec.*

  private lazy val registry: ActorRef[Command] = {
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    eventually {
      cluster.selfMember.status shouldBe MemberStatus.Up
    }
    implicit val sys = system
    spawn(
      GenericClusterAggregateRegistry.create[UserAccountId, Command]("TestAggregate")(
        extractId = value => Try(UserAccountId.from(value)),
        createIdleMessage = Stop(_),
        idleTimeout = Some(GenericClusterAggregateRegistry.NoIdleTimeout),
        enablePassivation = false
      )(aggregate)
    )
  }

  "GenericClusterAggregateRegistry" - {
    "子の集約がIllegalArgumentExceptionで止まったとき" - {
      "エンティティも止まり、次のメッセージで新しい集約が作られる" in {
        val id = UserAccountId.generate()
        val probe = createTestProbe[Pong]()

        registry ! Ping(id, probe.ref)
        val first = probe.receiveMessage().incarnation

        registry ! Fail(id)

        eventually {
          registry ! Ping(id, probe.ref)
          probe.receiveMessage(500.millis).incarnation should not be first
        }
      }
    }
  }
}

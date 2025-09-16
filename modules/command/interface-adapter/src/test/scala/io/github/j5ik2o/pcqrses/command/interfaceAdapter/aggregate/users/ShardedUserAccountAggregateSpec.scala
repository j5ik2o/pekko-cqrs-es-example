package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import com.typesafe.config.{Config, ConfigFactory}
import io.github.j5ik2o.pcqrses.command.domain.users.UserAccountId
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol.{Command, CreateReply}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.registry.{GenericAggregateRegistry, GenericClusterAggregateRegistry}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.test.ActorSpec
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.cluster.Cluster
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files
import java.util.{Comparator, UUID}

object ShardedUserAccountAggregateSpec {
  val id: String = UUID.randomUUID().toString

  val config: Config = ConfigFactory
    .parseString(
      s"""
       |pekko {
       |  actor {
       |    provider = cluster
       |  }
       |  cluster {
       |    seed-nodes = ["pekko://StaffClusterRegistrySpec@127.0.0.1:25520"]
       |    downing-provider-class = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
       |    sharding {
       |      number-of-shards = 100
       |      passivation {
       |        strategy = none
       |      }
       |    }
       |  }
       |
       |  remote.artery {
       |    canonical {
       |      hostname = "127.0.0.1"
       |      port = 25520
       |    }
       |  }
       |
       |  persistence {
       |    journal {
       |      plugin = "pekko.persistence.journal.inmem"
       |      inmem {
       |        class = "org.apache.pekko.persistence.journal.inmem.InmemJournal"
       |        plugin-dispatcher = "pekko.actor.default-dispatcher"
       |      }
       |    }
       |    snapshot-store {
       |      plugin = "pekko.persistence.snapshot-store.local"
       |      local {
       |        dir = "target/snapshot/$id"
       |      }
       |    }
       |  }
       |  test {
       |    single-expect-default = 5s
       |  }
       |}
       |""".stripMargin
    )
    .withFallback(ConfigFactory.load())

}

class ShardedUserAccountAggregateSpec
  extends ActorSpec(ShardedUserAccountAggregateSpec.config)
  with UserAccountTestHelper
  with Matchers
  with Eventually
  with BeforeAndAfterAll {
  private var registry: ActorRef[Command] = scala.compiletime.uninitialized
  private var cluster: Cluster = scala.compiletime.uninitialized

  override def beforeAll(): Unit = {
    super.beforeAll()

    // クラスターを起動
    cluster = Cluster(system)
    cluster.join(cluster.selfMember.address)

    // クラスターが形成されるまで待機
    eventually {
      cluster.state.members.nonEmpty shouldBe true
      cluster.state.members.head.status shouldBe org.apache.pekko.cluster.MemberStatus.Up
    }

    // ClusterModeのRegistryを作成（テスト用にタイムアウトとパッシベーションを無効化）
    implicit val sys = system
    registry = spawn(
      UserAccountAggregateRegistry.create(
        mode = GenericAggregateRegistry.Mode.ClusterMode,
        idleTimeout = Some(GenericClusterAggregateRegistry.NoIdleTimeout),
        enablePassivation = false
      ),
      "sharded-user-account-aggregate-registry"
    )
  }

  override def afterAll(): Unit = {
    // クラスターをシャットダウン
    cluster.leave(cluster.selfMember.address)

    // クラスターがシャットダウンするまで待機
    Thread.sleep(2000)

    super.afterAll()
    val snapshotDir = new java.io.File(s"target/snapshot/${ShardedUserAccountAggregateSpec.id}")
    if (snapshotDir.exists()) {
      Files
        .walk(snapshotDir.toPath())
        .sorted(Comparator.reverseOrder())
        .forEach(Files.delete(_))
    }
  }

  /**
   * 直接spawnしたアクターにコマンドを送信
   */
  override def sendCommand[Reply](
    userAccountId: UserAccountId,
    createCommand: UserAccountId => Command,
    probe: TestProbe[Reply]
  ): Unit = {
    registry ! createCommand(userAccountId)
  }

  "UserAccountAggregate" - {
    "ユーザアカウントが未作成の状態" - {
      "Createコマンドを受信したとき" - {
        "新しいユーザアカウントを作成できる" in
          testCreateUserAccountOnNotCreated()
      }

      "Getコマンドを受信したとき" - {
        "NotFoundを返す" in
          testGetUserAccountOnNotCreated()
      }
    }

    "ユーザアカウントが作成済みの状態" - {
      "Getコマンドを受信したとき" - {
        "ユーザアカウント情報を返す" in
          testGetUserAccountOnCreated()
      }

      "Renameコマンドを受信したとき" - {
        "ユーザアカウントの名前を変更できる" in
          testRenameUserAccountOnCreated()
      }

      "Deleteコマンドを受信したとき" - {
        "ユーザアカウントを削除できる" in
          testDeleteUserAccountOnCreated()
      }
    }

    "ユーザアカウントが削除済みの状態" - {
      "Getコマンドを受信したとき" - {
        "NotFoundを返す" in
          testGetUserAccountOnDeleted()
      }
    }
  }

  "ClusterSharding固有の動作" - {
    "複数IDに対する同時Createが独立して成功する" in {
      val ids = List.fill(5)(UserAccountId.generate())
      val probes = ids.map(_ => createTestProbe[CreateReply]())

      ids.zip(probes).foreach { case (id, probe) =>
        sendCommand(id, id0 => UserAccountAggregateSpecHelper.createCommand(id0, probe.ref), probe)
      }

      probes.foreach(_.expectMessageType[CreateReply])
    }

    "異なるID間で状態が干渉しない（Renameしても他IDは影響なし）" in {
      import io.github.j5ik2o.pcqrses.command.domain.users.*
      import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol.*

      val id1 = UserAccountId.generate()
      val id2 = UserAccountId.generate()

      // 2ユーザ作成
      createUserAccount(id1)
      createUserAccount(id2)

      // id1 をリネーム
      val newName = UserAccountName(FirstName("太一"), LastName("山田"))
      val renameProbe = createTestProbe[RenameReply]()
      sendCommand(id1, id => Rename(id, newName, renameProbe.ref), renameProbe)
      renameProbe.expectMessageType[RenameSucceeded]

      // id1 を取得して名前が更新されたことを確認
      val getProbe1 = createTestProbe[GetReply]()
      sendCommand(id1, id => Get(id, getProbe1.ref), getProbe1)
      val get1 = getProbe1.expectMessageType[GetSucceeded]
      get1.value.name shouldBe newName

      // id2 は元のままであることを確認
      val getProbe2 = createTestProbe[GetReply]()
      sendCommand(id2, id => Get(id, getProbe2.ref), getProbe2)
      val get2 = getProbe2.expectMessageType[GetSucceeded]
      get2.value.id shouldBe id2
      get2.value.name should not be newName
    }
  }
}

private object UserAccountAggregateSpecHelper {
  import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol.*
  import io.github.j5ik2o.pcqrses.command.domain.users.*

  def createCommand(id: UserAccountId, replyTo: org.apache.pekko.actor.typed.ActorRef[CreateReply]): Command =
    Create(id, UserAccountName(FirstName("花子"), LastName("鈴木")), EmailAddress("hanako@example.com"), replyTo)
}

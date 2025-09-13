package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import com.typesafe.config.{Config, ConfigFactory}
import io.github.j5ik2o.pcqrses.command.domain.users.UserAccountId
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol.Command
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.test.ActorSpec
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files
import java.util.{Comparator, UUID}

object UserAccountAggregateSpec {
  val id: String = UUID.randomUUID().toString

  val config: Config = ConfigFactory
    .parseString(
      s"""
       |pekko {
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

class UserAccountAggregateSpec
  extends ActorSpec(UserAccountAggregateSpec.config)
  with UserAccountTestHelper
  with Matchers
  with Eventually
  with BeforeAndAfterAll {
  override def afterAll(): Unit = {
    super.afterAll()
    val snapshotDir = new java.io.File(s"target/snapshot/${UserAccountAggregateSpec.id}")
    if (snapshotDir.exists()) {
      Files
        .walk(snapshotDir.toPath)
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
    val aggregate = spawn(UserAccountAggregate(userAccountId))
    aggregate ! createCommand(userAccountId)
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
}

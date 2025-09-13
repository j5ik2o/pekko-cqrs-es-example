package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import io.github.j5ik2o.pcqrses.command.domain.users.{
  EmailAddress,
  FirstName,
  LastName,
  UserAccountId,
  UserAccountName
}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol._
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.test.ActorSpec
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.scalatest.matchers.should.Matchers

trait UserAccountTestHelper { this: ActorSpec & Matchers =>
  def sendCommand[Reply](
    userAccountId: UserAccountId,
    createCommand: UserAccountId => Command,
    probe: TestProbe[Reply]
  ): Unit

  /**
   * テストヘルパー: ユーザアカウントを作成する
   */
  protected def createUserAccount(
    userAccountId: UserAccountId = UserAccountId.generate(),
    name: UserAccountName = UserAccountName(FirstName("花子"), LastName("鈴木")),
    email: EmailAddress = EmailAddress("hanako@example.com")
  ): CreateSucceeded = {
    val probe = createTestProbe[CreateReply]()
    sendCommand(
      userAccountId,
      id => Create(id, name, email, probe.ref),
      probe
    )
    probe.expectMessageType[CreateSucceeded]
  }

  /**
   * テストヘルパー: ユーザアカウントが未作成の状態でCreateコマンドを受信したときのテスト
   */
  protected def testCreateUserAccountOnNotCreated(): Unit = {
    val userAccountId = UserAccountId.generate()
    val name = UserAccountName(FirstName("太郎"), LastName("山田"))
    val email = EmailAddress("taro@example.com")

    val probe = createTestProbe[CreateReply]()
    sendCommand(
      userAccountId,
      id => Create(id, name, email, probe.ref),
      probe
    )

    val reply = probe.expectMessageType[CreateSucceeded]
    reply.id shouldBe userAccountId
  }

  /**
   * テストヘルパー: 作成済みユーザアカウントにGetコマンドを送信したときのテスト
   */
  protected def testGetUserAccountOnCreated(): Unit = {
    val userAccountId = UserAccountId.generate()
    val name = UserAccountName(FirstName("花子"), LastName("鈴木"))
    val email = EmailAddress("hanako@example.com")

    // まずユーザアカウントを作成
    createUserAccount(userAccountId, name, email)

    // Getコマンドを送信
    val getProbe = createTestProbe[GetReply]()
    sendCommand(
      userAccountId,
      id => Get(id, getProbe.ref),
      getProbe
    )

    val reply = getProbe.expectMessageType[GetSucceeded]
    reply.value.id shouldBe userAccountId
    reply.value.name shouldBe name
    reply.value.emailAddress shouldBe email
  }

  /**
   * テストヘルパー: 作成済みユーザアカウントの名前を変更するテスト
   */
  protected def testRenameUserAccountOnCreated(): Unit = {
    val userAccountId = UserAccountId.generate()
    val originalName = UserAccountName(FirstName("太郎"), LastName("山田"))
    val email = EmailAddress("taro@example.com")
    val newName = UserAccountName(FirstName("次郎"), LastName("山田"))

    // まずユーザアカウントを作成
    createUserAccount(userAccountId, originalName, email)

    // Renameコマンドを送信
    val renameProbe = createTestProbe[RenameReply]()
    sendCommand(
      userAccountId,
      id => Rename(id, newName, renameProbe.ref),
      renameProbe
    )

    val reply = renameProbe.expectMessageType[RenameSucceeded]
    reply.id shouldBe userAccountId

    // 名前が変更されたことを確認
    val getProbe = createTestProbe[GetReply]()
    sendCommand(
      userAccountId,
      id => Get(id, getProbe.ref),
      getProbe
    )

    val getReply = getProbe.expectMessageType[GetSucceeded]
    getReply.value.name shouldBe newName
  }

  /**
   * テストヘルパー: 作成済みユーザアカウントを削除するテスト
   */
  protected def testDeleteUserAccountOnCreated(): Unit = {
    val userAccountId = UserAccountId.generate()
    val name = UserAccountName(FirstName("太郎"), LastName("山田"))
    val email = EmailAddress("taro@example.com")

    // まずユーザアカウントを作成
    createUserAccount(userAccountId, name, email)

    // Deleteコマンドを送信
    val deleteProbe = createTestProbe[DeleteReply]()
    sendCommand(
      userAccountId,
      id => Delete(id, deleteProbe.ref),
      deleteProbe
    )

    val reply = deleteProbe.expectMessageType[DeleteSucceeded]
    reply.id shouldBe userAccountId

    // 削除後にGetコマンドを送信すると、NotFoundになることを確認
    val getProbe = createTestProbe[GetReply]()
    sendCommand(
      userAccountId,
      id => Get(id, getProbe.ref),
      getProbe
    )

    getProbe.expectMessageType[GetNotFoundFailed]
  }

  /**
   * テストヘルパー: 未作成のユーザアカウントにGetコマンドを送信したときのテスト
   */
  protected def testGetUserAccountOnNotCreated(): Unit = {
    val userAccountId = UserAccountId.generate()

    val probe = createTestProbe[GetReply]()
    sendCommand(
      userAccountId,
      id => Get(id, probe.ref),
      probe
    )

    val reply = probe.expectMessageType[GetNotFoundFailed]
    reply.id shouldBe userAccountId
  }

  /**
   * テストヘルパー: 削除済みユーザアカウントにGetコマンドを送信したときのテスト
   */
  protected def testGetUserAccountOnDeleted(): Unit = {
    val userAccountId = UserAccountId.generate()
    val name = UserAccountName(FirstName("太郎"), LastName("山田"))
    val email = EmailAddress("taro@example.com")

    // ユーザアカウントを作成して削除
    createUserAccount(userAccountId, name, email)

    val deleteProbe = createTestProbe[DeleteReply]()
    sendCommand(
      userAccountId,
      id => Delete(id, deleteProbe.ref),
      deleteProbe
    )
    deleteProbe.expectMessageType[DeleteSucceeded]

    // 削除済みユーザアカウントにGetコマンドを送信
    val getProbe = createTestProbe[GetReply]()
    sendCommand(
      userAccountId,
      id => Get(id, getProbe.ref),
      getProbe
    )

    val reply = getProbe.expectMessageType[GetNotFoundFailed]
    reply.id shouldBe userAccountId
  }

}

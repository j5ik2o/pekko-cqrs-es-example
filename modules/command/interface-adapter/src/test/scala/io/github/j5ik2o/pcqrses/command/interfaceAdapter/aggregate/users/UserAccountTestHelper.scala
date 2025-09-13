package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import io.github.j5ik2o.pcqrses.command.domain.users.{EmailAddress, FirstName, LastName, UserAccountId, UserAccountName}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol.{Command, Create, CreateReply, CreateSucceeded}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.test.ActorSpec
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.scalatest.matchers.should.Matchers

trait UserAccountTestBase { this: ActorSpec & Matchers =>
  def sendCommand[Reply](
                          userAccountId: UserAccountId,
                          createCommand: UserAccountId => Command,
                          probe: TestProbe[Reply]
                        ): Unit

  /**
   * テストヘルパー: スタッフを作成する
   */
  protected def createUserAccount(
                             userAccountId: UserAccountId = UserAccountId.generate(),
                             name: UserAccountName = UserAccountName(FirstName("花子"), LastName("鈴木")),
                             email: EmailAddress = EmailAddress("hanako@example.com"),
                           ): CreateSucceeded = {
    val probe = createTestProbe[CreateReply]()
    sendCommand(
      userAccountId,
      id => Create(id, name, email, probe.ref),
      probe
    )
    probe.expectMessageType[CreateSucceeded]
  }

}

package io.github.j5ik2o.pcqrses.command.useCase.users

import io.github.j5ik2o.pcqrses.command.domain.users.{EmailAddress, UserAccountId, UserAccountName}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol
import io.github.j5ik2o.pcqrses.infrastructure.effect.PekkoInterop
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.actor.typed.{ActorRef, Scheduler}
import org.apache.pekko.util.Timeout
import org.slf4j.LoggerFactory
import zio.{IO, Task, ZIO}

import scala.concurrent.ExecutionContext

/**
 * UserAccountUseCaseの実装クラス
 *
 * Pekkoアクターを使用して実際のビジネスロジックを実行する
 */
private[users] final class UserAccountUseCaseImpl(
  userAccountAggregateRef: ActorRef[UserAccountProtocol.Command]
)(implicit
  timeout: Timeout,
  scheduler: Scheduler,
  ec: ExecutionContext
) extends UserAccountUseCase {

  private val logger = LoggerFactory.getLogger(this.getClass)

  override def createUserAccount(
    userAccountName: UserAccountName,
    emailAddress: EmailAddress
  ): IO[UserAccountUseCaseError, UserAccountId] =
    for {
      _ <- ZIO.succeed(
        logger.info(s"Creating UserAccount with userAccountName: ${userAccountName.asString}")
      )
      userAccountId <- ZIO.succeed(UserAccountId.generate())
      reply <- askActor[UserAccountProtocol.CreateReply] { replyTo =>
        UserAccountProtocol.Create(
          id = userAccountId,
          name = userAccountName,
          emailAddress = emailAddress,
          replyTo = replyTo
        )
      }.mapError(e =>
        UserAccountUseCaseError.UnexpectedError(
          s"Failed to communicate with actor: ${e.getMessage}",
          Some(e)
        ))
      result <- reply match {
        case UserAccountProtocol.CreateSucceeded(id) =>
          ZIO.succeed(logger.info(s"UserAccount creation succeeded for ID: ${id.asString}")) *>
            ZIO.succeed(id)
      }
    } yield result

  private def askActor[R](
    createMessage: ActorRef[R] => UserAccountProtocol.Command
  ): Task[R] =
    PekkoInterop.fromFuture {
      userAccountAggregateRef.ask(createMessage)
    }
}

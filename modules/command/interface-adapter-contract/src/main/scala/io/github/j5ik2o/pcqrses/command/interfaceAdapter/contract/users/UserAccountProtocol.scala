package io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users

import io.github.j5ik2o.pcqrses.command.domain.users.{DeleteError, EmailAddress, RenameError, UserAccount, UserAccountId, UserAccountName}
import org.apache.pekko.actor.typed.ActorRef

object UserAccountProtocol {
  sealed trait Command
  final case class Create(id: UserAccountId, name: UserAccountName, emailAddress: EmailAddress, replyTo: ActorRef[CreateReply]) extends Command
  final case class Rename(id: UserAccountId, newName: UserAccountName, replyTo: ActorRef[RenameReply]) extends Command
  final case class Delete(id: UserAccountId, replyTo: ActorRef[DeleteReply]) extends Command
  final case class Get(id: UserAccountId, replyTo: ActorRef[GetReply]) extends Command

  sealed trait CreateReply
  final case class CreateSucceeded(id: UserAccountId) extends CreateReply

  sealed trait RenameReply
  final case class RenameSucceeded(id: UserAccountId) extends RenameReply
  final case class RenameFailed(id: UserAccountId, reason: RenameError) extends RenameReply

  sealed trait DeleteReply
  final case class DeleteSucceeded(id: UserAccountId) extends DeleteReply
  final case class DeleteFailed(id: UserAccountId, reason: DeleteError) extends DeleteReply

  sealed trait GetReply
  final case class GetSucceeded(value: UserAccount) extends GetReply
  final case class GetNotFoundFailed(id: UserAccountId) extends GetReply
}

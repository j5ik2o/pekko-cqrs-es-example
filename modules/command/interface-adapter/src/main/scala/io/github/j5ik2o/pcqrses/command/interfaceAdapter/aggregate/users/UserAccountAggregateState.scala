package io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users

import io.github.j5ik2o.pcqrses.command.domain.users.{UserAccount, UserAccountEvent, UserAccountId}

private[users] enum UserAccountAggregateState {
  case NotCreated(id: UserAccountId)
  case Created(user: UserAccount)
  case Deleted(user: UserAccount) // 削除状態でも全情報を保持

  def applyEvent(event: UserAccountEvent): UserAccountAggregateState = (this, event) match {
    case (NotCreated(id), UserAccountEvent.Created_V1(_, entityId, name, emailAddress, _))
        if id == entityId =>
      Created(UserAccount(entityId, name, emailAddress)._1)

    case (Created(user), UserAccountEvent.Renamed_V1(_, entityId, _, newName, _))
        if user.id == entityId =>
      Created(user.rename(newName) match {
        case Right((u, _)) => u
        case Left(error) =>
          throw new IllegalStateException(s"Failed to rename user: $error")
      })

    case (Created(user), UserAccountEvent.Deleted_V1(_, entityId, _)) if user.id == entityId =>
      Deleted(user.delete match {
        case Right((deletedUser, _)) => deletedUser
        case Left(error) =>
          throw new IllegalStateException(s"Failed to delete user: $error")
      })

    case (NotCreated(id), _) =>
      throw new IllegalStateException(s"Cannot apply event $event to NotCreated state with id $id")

    case (Created(user), _) =>
      throw new IllegalStateException(s"Cannot apply event $event to Created state with user $user")

    case (Deleted(user), _) =>
      throw new IllegalStateException(s"Cannot apply event $event to Deleted state with user $user")
  }

}

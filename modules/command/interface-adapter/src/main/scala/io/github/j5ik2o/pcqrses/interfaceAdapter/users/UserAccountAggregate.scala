package io.github.j5ik2o.pcqrses.interfaceAdapter.users

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistenceEffector,
  PersistenceEffectorConfig,
  PersistenceMode,
  RetentionCriteria,
  SnapshotCriteria
}
import io.github.j5ik2o.pcqrses.domain.users.{UserAccount, UserAccountEvent, UserAccountId}
import io.github.j5ik2o.pcqrses.interfaceAdapter.contract.users.UserAccountProtocol.*
import org.apache.pekko.actor.typed.{Behavior, SupervisorStrategy}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId

object UserAccountAggregate {

  private def handleNotCreated(
    state: UserAccountAggregateState.NotCreated,
    effector: PersistenceEffector[UserAccountAggregateState, UserAccountEvent, Command])
    : Behavior[Command] = Behaviors.receiveMessagePartial {
    case Create(id, name, emailAddress, replyTo) if state.id == id =>
      val (newState, event) = UserAccount(id, name, emailAddress)
      effector
        .persistEvent(event) { _ =>
          replyTo ! CreateSucceeded(id)
          handleCreated(UserAccountAggregateState.Created(newState), effector)
        }
    case Get(id, replyTo) if state.id == id =>
      replyTo ! GetNotFoundFailed(id)
      Behaviors.same
  }

  private def handleCreated(
    state: UserAccountAggregateState.Created,
    effector: PersistenceEffector[UserAccountAggregateState, UserAccountEvent, Command])
    : Behavior[Command] =
    Behaviors.receiveMessagePartial {
      case Rename(id, newName, replyTo) if state.user.id == id =>
        state.user.rename(newName) match {
          case Left(reason) =>
            replyTo ! RenameFailed(id, reason)
            Behaviors.same
          case Right((newUser, event)) =>
            effector.persistEvent(event) { _ =>
              replyTo ! RenameSucceeded(id)
              handleCreated(state.copy(user = newUser), effector)
            }
        }
      case Delete(id, replyTo) if state.user.id == id =>
        state.user.delete match {
          case Left(reason) =>
            replyTo ! DeleteFailed(id, reason)
            Behaviors.same
          case Right((newUser, event)) =>
            effector.persistEvent(event) { _ =>
              replyTo ! DeleteSucceeded(id)
              handleDeleted(UserAccountAggregateState.Deleted(newUser), effector)
            }
        }
      case Get(id, replyTo) if state.user.id == id =>
        replyTo ! GetSucceeded(state.user)
        Behaviors.same
    }

  private def handleDeleted(
    state: UserAccountAggregateState.Deleted,
    effector: PersistenceEffector[UserAccountAggregateState, UserAccountEvent, Command])
    : Behavior[Command] = Behaviors.receiveMessagePartial { case Get(id, replyTo) =>
    replyTo ! GetNotFoundFailed(id)
    Behaviors.same
  }

  def apply(id: UserAccountId): Behavior[Command] = {
    val config = PersistenceEffectorConfig
      .create[UserAccountAggregateState, UserAccountEvent, Command](
        persistenceId = PersistenceId.of(id.entityTypeName, id.asString).id,
        initialState = UserAccountAggregateState.NotCreated(id),
        applyEvent = (state, event) => state.applyEvent(event)
      )
      .withPersistenceMode(PersistenceMode.Persisted)
      .withSnapshotCriteria(SnapshotCriteria.every(1000))
      .withRetentionCriteria(RetentionCriteria.snapshotEvery(2))
    Behaviors.setup[Command] { implicit ctx =>
      Behaviors
        .supervise(
          PersistenceEffector
            .fromConfig[UserAccountAggregateState, UserAccountEvent, Command](
              config
            ) {
              case (initialState: UserAccountAggregateState.NotCreated, effector) =>
                handleNotCreated(initialState, effector)
              case (initialState: UserAccountAggregateState.Created, effector) =>
                handleCreated(initialState, effector)
              case (initialState: UserAccountAggregateState.Deleted, effector) =>
                handleDeleted(initialState, effector)
            })
        .onFailure[IllegalArgumentException](
          SupervisorStrategy.restart
        )
    }
  }
}

package io.github.j5ik2o.pcqrses.queryApi

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}

object MainActor extends App {
  private sealed trait Command
  private case object Start extends Command
  private case object Stop extends Command

  private def apply(): Behavior[Command] = Behaviors.setup[Command] { context =>
    implicit val executionContext: ExecutionContextExecutor = system.executionContext
    // TODO: GraphQL Handler(Query) の初期化
    Behaviors.empty
  }

  private val system: ActorSystem[Command] = ActorSystem(apply(), "query-api-system")
  system ! Start

  Await.result(system.whenTerminated, Duration.Inf)
}

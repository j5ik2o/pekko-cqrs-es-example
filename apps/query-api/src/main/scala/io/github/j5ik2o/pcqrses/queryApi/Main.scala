package io.github.j5ik2o.pcqrses.queryApi
import org.apache.pekko.actor.typed.ActorSystem

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main extends App {
  private val system: ActorSystem[MainActor.Command] = ActorSystem(MainActor(), "query-api-system")
  system ! MainActor.Start
  Await.result(system.whenTerminated, Duration.Inf)
}

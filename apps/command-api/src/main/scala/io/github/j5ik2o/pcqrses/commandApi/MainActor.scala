package io.github.j5ik2o.pcqrses.commandApi

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users.UserAccountAggregateRegistry
import io.github.j5ik2o.pcqrses.command.useCase.users.UserAccountUseCase
import io.github.j5ik2o.pcqrses.commandApi.config.{LoadBalancerConfig, ServerConfig}
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{scaladsl, ActorSystem, Behavior, Scheduler}
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap
import org.apache.pekko.management.scaladsl.PekkoManagement
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.Timeout
import org.apache.pekko.{pattern, Done}
import zio.*

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object MainActor {
  sealed trait Command
  case object Start extends Command
  case object Stop extends Command
  private case class ServerStarted(binding: Http.ServerBinding, startTime: Long) extends Command
  private case class ServerFailed(ex: Throwable, startTime: Long) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      implicit val system: ActorSystem[Nothing] = context.system
      implicit val materializer: Materializer = Materializer(system)
      implicit val executionContext: ExecutionContextExecutor = system.executionContext
      implicit val scheduler: Scheduler = system.scheduler
      implicit val zioRuntime: Runtime[Any] = Runtime.default

      val serverConfig = ServerConfig.from(system.settings.config.getConfig("pcqrses.command-api"))
      val isClusterEnabled = system.settings.config.hasPath("pekko.cluster.enabled") &&
        system.settings.config.getBoolean("pekko.cluster.enabled")

      implicit val timeout: Timeout = Timeout(serverConfig.actorTimeout)

      context.log.info(
        s"Command API server initializing... (timeout: ${serverConfig.actorTimeout}, host: ${serverConfig.host}:${serverConfig.port}, cluster: $isClusterEnabled)")

      if (isClusterEnabled) {
        initializeCluster(context)
      }

      // TODO: GraphQL Handler(Mutation) の初期化
      initializeGraphQLHandler(context)

      Behaviors.same
    }

  private def initializeGraphQLHandler(context: scaladsl.ActorContext[Command])(implicit
    system: ActorSystem[?],
    executionContext: ExecutionContextExecutor,
    zioRuntime: Runtime[Any]
  ): Unit = {
    val serverConfig = ServerConfig.from(system.settings.config.getConfig("pcqrses.command-api"))
    implicit val timeout: Timeout = Timeout(serverConfig.actorTimeout)
    implicit val scheduler: Scheduler = system.scheduler

    val mode = UserAccountAggregateRegistry.modeFromConfig(system)
    val b = UserAccountAggregateRegistry.create(mode)
    val ref = context.spawn(b, "UserAccountAggregateRegistry")
    val h: UserAccountUseCase = UserAccountUseCase(ref)
    // GraphQLHandlerの初期化ロジックをここに実装
    context.log.info("GraphQL Handler initialized")
  }

  private def startManagementWithGracefulShutdown(
    context: scaladsl.ActorContext[Command],
    management: PekkoManagement,
    coordinatedShutdown: CoordinatedShutdown,
    lbConfig: LoadBalancerConfig
  )(implicit
    executionContext: ExecutionContextExecutor,
    system: ActorSystem[?]
  ): Unit = {
    val managementFuture = management.start().map { uri =>
      context.log.info(s"Pekko Management started on $uri")

      coordinatedShutdown.addTask(
        CoordinatedShutdown.PhaseBeforeServiceUnbind,
        "management-loadbalancer-detach") { () =>
        for {
          _ <- Future {
            context.log.info(
              s"Starting graceful shutdown - waiting ${lbConfig.detachWaitDuration} for LoadBalancer detach")
          }
          _ <- pattern.after(lbConfig.detachWaitDuration) {
            Future {
              context.log.info("LoadBalancer detach wait completed")
            }
          }
          _ <- management.stop()
          _ <- Future {
            context.log.info("Pekko Management terminated")
          }
        } yield Done
      }
      Done
    }

    try
      Await.result(managementFuture, 10.seconds)
    catch {
      case ex: java.util.concurrent.TimeoutException =>
        context.log.error(s"Pekko Management start timed out: ${ex.getMessage}")
      case ex: Throwable =>
        context.log.error(s"Failed to start Pekko Management: ${ex.getMessage}")
    }
  }

  private def initializeCluster(context: scaladsl.ActorContext[Command]): Unit = {
    implicit val system: ActorSystem[Nothing] = context.system
    implicit val executionContext: ExecutionContextExecutor = system.executionContext

    val cluster = Cluster(system)
    context.log.info(s"Initializing cluster node: ${cluster.selfMember.address}")

    val management = PekkoManagement(system)
    val coordinatedShutdown = CoordinatedShutdown(system)
    val config = system.settings.config

    val lbConfig = LoadBalancerConfig.from(config.getConfig("pcqrses.command-api"))

    startManagementWithGracefulShutdown(context, management, coordinatedShutdown, lbConfig)

    if (system.settings.config.hasPath(
        "pekko.management.cluster.bootstrap.contact-point-discovery.discovery-method")) {
      ClusterBootstrap(system).start()
      context.log.info("Cluster Bootstrap started")
    }
  }

}

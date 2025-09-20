package io.github.j5ik2o.pcqrses.commandApi

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users.UserAccountAggregateRegistry
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.GraphQLService
import io.github.j5ik2o.pcqrses.command.useCase.users.UserAccountUseCase
import io.github.j5ik2o.pcqrses.commandApi.config.{LoadBalancerConfig, ServerConfig}
import io.github.j5ik2o.pcqrses.commandApi.routes.GraphQLRoutes
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{scaladsl, ActorSystem, Behavior, Scheduler}
import org.apache.pekko.cluster.typed.Cluster
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
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
  private case class ServerStarted(binding: Http.ServerBinding) extends Command
  private case class ServerFailed(ex: Throwable) extends Command

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

      // UserAccountUseCaseとGraphQLサービスの初期化
      val userAccountUseCase = initializeCommandServices(context)

      // GraphQLサービスの初期化
      val graphQLService = GraphQLService(userAccountUseCase)
      context.log.info("GraphQL service initialized")

      // ルートの定義
      val graphQLRoutes = new GraphQLRoutes(graphQLService)
      val routes = pathPrefix("api") {
        graphQLRoutes.routes
      }

      Behaviors.receiveMessage {
        case Start =>
          context.log.info(
            s"Starting Command API server on ${serverConfig.host}:${serverConfig.port}")

          // HTTPサーバーの起動
          val serverBinding = Http()
            .newServerAt(serverConfig.host, serverConfig.port)
            .bind(routes)

          context.pipeToSelf(serverBinding) {
            case scala.util.Success(binding) => ServerStarted(binding)
            case scala.util.Failure(ex) => ServerFailed(ex)
          }

          // グレースフルシャットダウンの設定
          CoordinatedShutdown(system).addTask(
            CoordinatedShutdown.PhaseServiceUnbind,
            "http-server-unbind"
          ) { () =>
            serverBinding.flatMap(_.unbind()).map(_ => Done)
          }

          Behaviors.same

        case ServerStarted(binding) =>
          context.log.info(s"Command API server started: ${binding.localAddress}")
          Behaviors.same

        case ServerFailed(ex) =>
          context.log.error(s"Failed to start Command API server", ex)
          throw ex

        case Stop =>
          context.log.info("Stopping Command API server")
          Behaviors.stopped
      }
    }

  private def initializeCommandServices(context: scaladsl.ActorContext[Command])(implicit
    system: ActorSystem[?],
    executionContext: ExecutionContextExecutor,
    zioRuntime: Runtime[Any]
  ): UserAccountUseCase = {
    val serverConfig = ServerConfig.from(system.settings.config.getConfig("pcqrses.command-api"))
    implicit val timeout: Timeout = Timeout(serverConfig.actorTimeout)
    implicit val scheduler: Scheduler = system.scheduler

    val mode = UserAccountAggregateRegistry.modeFromConfig(system)
    val behavior = UserAccountAggregateRegistry.create(mode)
    val aggregateRef = context.spawn(behavior, "UserAccountAggregateRegistry")

    context.log.info("UserAccountAggregateRegistry initialized")
    UserAccountUseCase(aggregateRef)
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

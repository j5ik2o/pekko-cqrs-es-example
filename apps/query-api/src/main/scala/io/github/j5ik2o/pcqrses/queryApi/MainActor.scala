package io.github.j5ik2o.pcqrses.queryApi

import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.GraphQLService
import io.github.j5ik2o.pcqrses.queryApi.config.ServerConfig
import io.github.j5ik2o.pcqrses.queryApi.routes.GraphQLRoutes
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.stream.Materializer
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object MainActor {
  sealed trait Command
  case object Start extends Command
  case object Stop extends Command
  private case class ServerStarted(binding: Http.ServerBinding) extends Command
  private case class ServerFailed(ex: Throwable) extends Command

  def apply(): Behavior[Command] = Behaviors.setup[Command] { context =>
    context.log.info("Setting up MainActor")
    implicit val system: ActorSystem[Nothing] = context.system
    implicit val materializer: Materializer = Materializer(system)
    implicit val executionContext: ExecutionContextExecutor = system.executionContext

    val config = system.settings.config
    val serverConfig = ServerConfig.from(config)

    context.log.info(s"Initializing Query API server with config: host=${serverConfig.host}, port=${serverConfig.port}")

    // データベース接続の初期化
    val dbConfig = config.getConfig("pcqrses.database")
    val db = Database.forConfig("", dbConfig)
    context.log.info("Database connection initialized")

    // GraphQLサービスの初期化
    val graphQLService = GraphQLService(PostgresProfile, db)
    context.log.info("GraphQL service initialized")

    // ルートの定義
    val graphQLRoutes = new GraphQLRoutes(graphQLService)
    val routes = pathPrefix("api") {
      graphQLRoutes.routes
    }

    Behaviors.receiveMessage {
      case Start =>
        context.log.info(s"Starting Query API server on ${serverConfig.host}:${serverConfig.port}")

        // HTTPサーバーの起動
        val serverBinding = Http()
          .newServerAt(serverConfig.host, serverConfig.port)
          .bind(routes)

        context.pipeToSelf(serverBinding) {
          case Success(binding) => ServerStarted(binding)
          case Failure(ex) => ServerFailed(ex)
        }

        // グレースフルシャットダウンの設定
        CoordinatedShutdown(system).addTask(
          CoordinatedShutdown.PhaseServiceUnbind, "http-server-unbind"
        ) { () =>
          serverBinding.flatMap(_.unbind()).map(_ => org.apache.pekko.Done)
        }

        Behaviors.same

      case ServerStarted(binding) =>
        context.log.info(s"Query API server started: ${binding.localAddress}")
        Behaviors.same

      case ServerFailed(ex) =>
        context.log.error(s"Failed to start Query API server", ex)
        throw ex

      case Stop =>
        context.log.info("Stopping Query API server")
        db.close()
        Behaviors.stopped
    }
  }

}




package io.github.j5ik2o.pcqrses.commandApi.routes

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.GraphQLService
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport._
import io.circe.Json
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext

/**
 * GraphQLエンドポイントのルート定義（Command側）
 */
class GraphQLRoutes(graphQLService: GraphQLService)(implicit ec: ExecutionContext) {

  /**
   * GraphQL用リクエストボディ
   */
  case class GraphQLRequest(
    query: String,
    operationName: Option[String] = None,
    variables: Option[Json] = None
  )

  /**
   * GraphQL Playgroundの簡易HTML
   */
  private val playgroundHtml = """
    |<!DOCTYPE html>
    |<html>
    |<head>
    |  <meta charset="utf-8">
    |  <title>GraphQL Playground - Command API</title>
    |  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/graphql-playground-react@1.7.26/build/static/css/index.css" />
    |  <script src="https://cdn.jsdelivr.net/npm/graphql-playground-react@1.7.26/build/static/js/middleware.js"></script>
    |</head>
    |<body>
    |  <div id="root"></div>
    |  <script>
    |    window.addEventListener('load', function (event) {
    |      GraphQLPlayground.init(document.getElementById('root'), {
    |        endpoint: '/api/graphql',
    |        settings: {
    |          'request.credentials': 'same-origin'
    |        }
    |      })
    |    })
    |  </script>
    |</body>
    |</html>
  """.stripMargin

  val routes: Route =
    concat(
      path("graphql") {
        concat(
          // GraphQLミューテーションを処理
          post {
            entity(as[GraphQLRequest]) { request =>
              complete {
                graphQLService
                  .executeQuery(
                    query = request.query,
                    operationName = request.operationName,
                    variables = request.variables
                  )
                  .map { result =>
                    StatusCodes.OK -> result
                  }
                  .recover { case ex: Exception =>
                    StatusCodes.InternalServerError -> Json.obj(
                      "errors" -> Json.arr(
                        Json.obj("message" -> Json.fromString(ex.getMessage))
                      )
                    )
                  }
              }
            }
          },
          // GraphQL Playgroundを表示
          get {
            complete(
              HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, playgroundHtml)))
          }
        )
      },
      // GraphQL Playgroundの別エンドポイント
      path("playground") {
        get {
          complete(
            HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, playgroundHtml)))
        }
      },
      // GraphQL introspection専用エンドポイント
      path("graphql" / "schema") {
        get {
          complete {
            graphQLService.introspectionQuery().map { result =>
              StatusCodes.OK -> result
            }
          }
        }
      },
      // ヘルスチェック
      path("health") {
        get {
          complete(StatusCodes.OK -> Json.obj("status" -> Json.fromString("healthy")))
        }
      }
    )
}

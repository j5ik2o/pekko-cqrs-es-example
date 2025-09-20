package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.errors.GraphQLErrorHandler
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.schema.GraphQLSchema
import io.github.j5ik2o.pcqrses.command.useCase.users.UserAccountUseCase
import sangria.execution.{ErrorWithResolver, Executor, QueryReducer}
import sangria.marshalling.circe.*
import sangria.parser.{QueryParser, SyntaxError}
import io.circe.Json
import io.circe.parser.*
import zio.Runtime

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * GraphQL実行サービス (Command側)
 * 
 * GraphQLミューテーションの解析、実行、エラーハンドリングを担当
 */
class GraphQLService(
  userAccountUseCase: UserAccountUseCase
)(implicit ec: ExecutionContext, zioRuntime: Runtime[Any]) {
  
  private val graphQLSchema = GraphQLSchema()
  private val schema = graphQLSchema.schema
  
  /**
   * GraphQLクエリを実行
   * 
   * @param query GraphQLクエリ文字列
   * @param operationName オプションのオペレーション名
   * @param variables オプションの変数マップ
   * @param isIntrospection イントロスペクションクエリかどうか
   * @return 実行結果のJSON
   */
  def executeQuery(
    query: String,
    operationName: Option[String] = None,
    variables: Option[Json] = None,
    isIntrospection: Boolean = false
  ): Future[Json] = {
    QueryParser.parse(query) match {
      case Success(queryAst) =>
        val context = ResolverContext(userAccountUseCase, zioRuntime)
        val vars = variables.getOrElse(Json.obj())
        
        // introspectionクエリの場合は深さ制限を緩和
        val maxDepth = if (isIntrospection) 30 else 10
        
        Executor.execute(
          schema = schema,
          queryAst = queryAst,
          userContext = context,
          variables = vars,
          operationName = operationName,
          queryReducers = List(
            QueryReducer.rejectMaxDepth(maxDepth),
            QueryReducer.rejectComplexQueries(1000.0, (complexity: Double, _: Any) =>
              new Exception(s"Query too complex: $complexity"))
          ),
          exceptionHandler = GraphQLErrorHandler.exceptionHandler
        ).recover {
          case error: ErrorWithResolver =>
            Json.obj("errors" -> Json.arr(Json.obj("message" -> Json.fromString(error.getMessage))))
        }
        
      case Failure(error: SyntaxError) =>
        Future.successful(
          Json.obj(
            "errors" -> Json.arr(
              Json.obj(
                "message" -> Json.fromString(s"Syntax error: ${error.getMessage}"),
                "locations" -> Json.arr(
                  Json.obj(
                    "line" -> Json.fromInt(error.originalError.position.line),
                    "column" -> Json.fromInt(error.originalError.position.column)
                  )
                )
              )
            )
          )
        )
        
      case Failure(error) =>
        Future.successful(
          Json.obj("errors" -> Json.arr(Json.obj("message" -> Json.fromString(error.getMessage))))
        )
    }
  }
  
  /**
   * JSONリクエストからGraphQLクエリを実行
   * 
   * @param requestJson リクエストJSON（query, operationName, variablesを含む）
   * @return 実行結果のJSON
   */
  def executeQueryFromJson(requestJson: String): Future[Json] = {
    parse(requestJson) match {
      case Right(json) =>
        val query = json.hcursor.downField("query").as[String].toOption
        val operationName = json.hcursor.downField("operationName").as[String].toOption
        val variables = json.hcursor.downField("variables").as[Json].toOption
        
        query match {
          case Some(q) => executeQuery(q, operationName, variables, isIntrospection = false)
          case None =>
            Future.successful(
              Json.obj("errors" -> Json.arr(Json.obj("message" -> Json.fromString("No query provided"))))
            )
        }
        
      case Left(error) =>
        Future.successful(
          Json.obj("errors" -> Json.arr(Json.obj("message" -> Json.fromString(s"Invalid JSON: ${error.getMessage}"))))
        )
    }
  }
  
  /**
   * GraphQLスキーマのイントロスペクションクエリを実行
   * 
   * @return スキーマ情報のJSON
   */
  def introspectionQuery(): Future[Json] = {
    import sangria.renderer.QueryRenderer
    val introspectionQueryString = QueryRenderer.render(sangria.introspection.introspectionQuery)
    executeQuery(introspectionQueryString, isIntrospection = true)
  }
}

object GraphQLService {
  /**
   * GraphQLServiceインスタンスを生成
   * 
   * @param userAccountUseCase UserAccountUseCase
   * @param ec ExecutionContext
   * @param zioRuntime ZIOランタイム
   * @return GraphQLServiceインスタンス
   */
  def apply(userAccountUseCase: UserAccountUseCase)(
    implicit ec: ExecutionContext, zioRuntime: Runtime[Any]): GraphQLService =
    new GraphQLService(userAccountUseCase)
}

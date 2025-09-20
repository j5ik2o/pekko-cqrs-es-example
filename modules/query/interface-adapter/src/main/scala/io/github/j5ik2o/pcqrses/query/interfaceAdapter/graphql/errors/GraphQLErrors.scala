package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.errors

import sangria.execution.{ExceptionHandler, HandledException, UserFacingError}
import sangria.marshalling.ResultMarshaller

/**
 * GraphQLのバリデーションエラー
 * 複数のエラーメッセージをクライアントに表示できる
 */
case class ValidationError(errors: Seq[String]) extends Exception with UserFacingError {
  override def getMessage: String = errors.mkString(", ")
}

/**
 * GraphQLのクエリ実行エラー
 */
case class QueryError(message: String, code: Option[String] = None) extends Exception(message) with UserFacingError {
  override def getMessage: String = message
}

/**
 * GraphQL用のエラーハンドラー
 */
object GraphQLErrorHandler {
  
  /**
   * Sangria用のExceptionHandler
   * UserFacingErrorを適切に処理して、クライアントに返す
   */
  def exceptionHandler(implicit marshaller: ResultMarshaller): ExceptionHandler = ExceptionHandler {
    case (_, error: ValidationError) =>
      HandledException(
        error.getMessage,
        Map(
          "errors" -> marshaller.arrayNode(error.errors.map(e => marshaller.scalarNode(e, "", Set.empty)).toVector),
          "type" -> marshaller.scalarNode("ValidationError", "", Set.empty)
        )
      )
    
    case (_, error: QueryError) =>
      HandledException(
        error.getMessage,
        Map(
          "type" -> marshaller.scalarNode("QueryError", "", Set.empty),
          "code" -> error.code.map(c => marshaller.scalarNode(c, "", Set.empty)).getOrElse(marshaller.nullNode)
        )
      )
    
    case (_, error: UserFacingError) =>
      HandledException(error.getMessage)
  }
}
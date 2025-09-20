package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.schema

import sangria.ast.StringValue
import sangria.schema.ScalarType
import sangria.validation.ValueCoercionViolation

import java.time.Instant
import java.time.format.DateTimeFormatter

case object DateTimeCoercionViolation extends ValueCoercionViolation("Date value expected")

/**
 * GraphQLのスカラー型定義
 */
trait ScalarTypes {
  
  /**
   * ISO 8601形式のDateTime型
   */
  val DateTimeType: ScalarType[Instant] = ScalarType[Instant](
    "DateTime",
    description = Some("An ISO-8601 encoded datetime"),
    coerceOutput = (value, _) => DateTimeFormatter.ISO_INSTANT.format(value),
    coerceUserInput = {
      case s: String =>
        scala.util.Try(Instant.parse(s)).toEither.left.map(_ => DateTimeCoercionViolation)
      case _ =>
        Left(DateTimeCoercionViolation)
    },
    coerceInput = {
      case StringValue(s, _, _, _, _) =>
        scala.util.Try(Instant.parse(s)).toEither.left.map(_ => DateTimeCoercionViolation)
      case _ =>
        Left(DateTimeCoercionViolation)
    }
  )
}
package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.schema

import sangria.ast.StringValue
import sangria.schema.ScalarType
import sangria.validation.ValueCoercionViolation

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object ScalarTypes {
  case object DateTimeCoercionViolation extends ValueCoercionViolation("Date/time value expected")

  implicit val OffsetDateTimeType: ScalarType[OffsetDateTime] = ScalarType[OffsetDateTime](
    "DateTime",
    description = Some("ISO-8601 compliant date-time with offset"),
    coerceOutput = (value, _) => value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    coerceUserInput = {
      case s: String =>
        try
          Right(OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        catch {
          case _: Exception =>
            Left(DateTimeCoercionViolation)
        }
      case _ =>
        Left(DateTimeCoercionViolation)
    },
    coerceInput = {
      case StringValue(s, _, _, _, _) =>
        try
          Right(OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        catch {
          case _: Exception =>
            Left(DateTimeCoercionViolation)
        }
      case _ =>
        Left(DateTimeCoercionViolation)
    }
  )
}
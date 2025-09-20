package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.schema

import io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao.UserAccountsComponent
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.ResolverContext
import sangria.schema.*
import ScalarTypes.*

import java.time.OffsetDateTime
import sangria.marshalling.FromInput.CoercedScalaResult
import sangria.util.tag.Tagged

trait TypeDefinitions {
  this: UserAccountsComponent =>

  val UserAccountIdArg: Argument[String] =
    Argument("userAccountId", StringType, description = "Id of UserAccount")

  val UserAccountIdsArg: Argument[Seq[String & Tagged[CoercedScalaResult] | Null]] =
    Argument("userAccountIds", ListInputType(StringType), description = "List of UserAccount IDs")

  val UserAccountType: ObjectType[ResolverContext, UserAccountsRecord] = ObjectType(
    "UserAccount",
    "User account information",
    fields[ResolverContext, UserAccountsRecord](
      Field("id", StringType, description = Some("Unique identifier"), resolve = _.value.id),
      Field(
        "firstName",
        StringType,
        description = Some("User's first name"),
        resolve = _.value.firstName),
      Field(
        "lastName",
        StringType,
        description = Some("User's last name"),
        resolve = _.value.lastName),
      Field(
        "fullName",
        StringType,
        description = Some("User's full name"),
        resolve = ctx => s"${ctx.value.firstName} ${ctx.value.lastName}"),
      Field(
        "createdAt",
        OffsetDateTimeType,
        description = Some("Account creation timestamp"),
        resolve =
          t => OffsetDateTime.ofInstant(t.value.createdAt.toInstant, java.time.ZoneOffset.UTC)
      ),
      Field(
        "updatedAt",
        OffsetDateTimeType,
        description = Some("Last update timestamp"),
        resolve =
          t => OffsetDateTime.ofInstant(t.value.updatedAt.toInstant, java.time.ZoneOffset.UTC)
      )
    )
  )
}

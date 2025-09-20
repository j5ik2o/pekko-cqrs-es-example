package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.validators

import io.github.j5ik2o.pcqrses.command.domain.users.{
  EmailAddress,
  FirstName,
  LastName,
  UserAccountName
}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.schema.CreateUserAccountInput
import zio.prelude.Validation

object CreateUserAccountInputValidator {
  type ErrorInfo = String

  def validate(
    input: CreateUserAccountInput): Validation[ErrorInfo, (UserAccountName, EmailAddress)] =
    Validation.validateWith(
      Validation.fromEither(
        FirstName
          .parseFromString(input.firstName)
          .left
          .map(e => s"Invalid first name: ${e.message}")
      ),
      Validation.fromEither(
        LastName
          .parseFromString(input.lastName)
          .left
          .map(e => s"Invalid last name: ${e.message}")
      ),
      Validation.fromEither(
        EmailAddress
          .parseFromString(input.emailAddress)
          .left
          .map(e => s"Invalid email: ${e.message}")
      )
    )((firstName, lastName, emailAddress) => (UserAccountName(firstName, lastName), emailAddress))
}

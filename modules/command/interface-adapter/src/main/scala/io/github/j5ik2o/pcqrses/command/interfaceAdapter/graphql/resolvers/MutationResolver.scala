package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.resolvers

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.ResolverContext
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.errors.{CommandError, ValidationError}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.schema.{TypeDefinitions, UserAccountOutput}
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.validators.CreateUserAccountInputValidator
import sangria.schema.*

import java.time.Instant

/**
 * GraphQL Mutation リゾルバー
 */
trait MutationResolver extends TypeDefinitions {
  
  val MutationType: ObjectType[ResolverContext, Unit] = ObjectType(
    "Mutation",
    "Root mutation type",
    fields[ResolverContext, Unit](
      Field(
        "createUserAccount",
        UserAccountType,
        description = Some("Create a new user account"),
        arguments = CreateUserAccountInputArg :: Nil,
        resolve = ctx => {
          val input = ctx.arg(CreateUserAccountInputArg)
          
          CreateUserAccountInputValidator.validate(input).toEither match {
            case Left(errors) =>
              // ValidationErrorを使用してバリデーションエラーを返す
              scala.concurrent.Future.failed(ValidationError(errors.toList))
            case Right((userAccountName, emailAddress)) =>
              ctx.ctx.runZioTask(
                ctx.ctx.userAccountUseCase
                  .createUserAccount(userAccountName, emailAddress)
                  .mapBoth(
                    // CommandErrorを使用してコマンド実行エラーを返す
                    error => CommandError(s"Failed to create user account: ${error.toString}", Some("CREATE_USER_FAILED")),
                    userAccountId => UserAccountOutput(
                      id = userAccountId.asString,
                      name = input.name,
                      emailAddress = input.emailAddress,
                      createdAt = Instant.now(),
                      updatedAt = None
                    )
                  )
              )
          }
        }
      )
    )
  )
}

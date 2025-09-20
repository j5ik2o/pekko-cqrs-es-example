package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.resolvers

import io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao.UserAccountsComponent
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.ResolverContext
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.errors.{QueryError, ValidationError}
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.schema.TypeDefinitions
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.validators.QueryInputValidator
import sangria.schema.*

trait QueryResolver extends TypeDefinitions {
  this: UserAccountsComponent =>

  val QueryType: ObjectType[ResolverContext, Unit] = ObjectType(
    "Query",
    "Root query type",
    fields[ResolverContext, Unit](
      Field(
        "getUserAccount",
        OptionType(UserAccountType),
        description = Some("Get a single user account by ID"),
        arguments = UserAccountIdArg :: Nil,
        resolve = ctx => {
          val id = ctx.arg(UserAccountIdArg)
          QueryInputValidator.validateUserAccountId(id).toEither match {
            case Left(errors) =>
              scala.concurrent.Future.failed(ValidationError(errors.toList))
            case Right(validId) =>
              ctx.ctx
                .runDbAction(UserAccountsDao.findById(validId))
                .recover { case ex: Exception =>
                  throw QueryError(
                    s"Failed to fetch user account: ${ex.getMessage}",
                    Some("FETCH_USER_FAILED"))
                }(ctx.ctx.ec)
          }
        }
      ),
      Field(
        "getUserAccounts",
        ListType(UserAccountType),
        description = Some("Get all user accounts"),
        resolve = ctx => ctx.ctx.runDbAction(UserAccountsDao.findAll())
      ),
      Field(
        "getUserAccountsByIds",
        ListType(UserAccountType),
        description = Some("Get multiple user accounts by IDs"),
        arguments = UserAccountIdsArg :: Nil,
        resolve = ctx => {
          val ids = ctx.arg(UserAccountIdsArg).asInstanceOf[Seq[String]]
          QueryInputValidator.validateUserAccountIds(ids).toEither match {
            case Left(errors) =>
              scala.concurrent.Future.failed(ValidationError(errors.toList))
            case Right(validIds) =>
              ctx.ctx
                .runDbAction(UserAccountsDao.findByIds(validIds))
                .recover { case ex: Exception =>
                  throw QueryError(
                    s"Failed to fetch user accounts: ${ex.getMessage}",
                    Some("FETCH_USERS_FAILED"))
                }(ctx.ctx.ec)
          }
        }
      ),
      Field(
        "searchUserAccounts",
        ListType(UserAccountType),
        description = Some("Search user accounts by name"),
        arguments = Argument("searchTerm", StringType, description = "Search term for name") :: Nil,
        resolve = ctx => {
          val searchTerm = ctx.arg[String]("searchTerm")
          QueryInputValidator.validateSearchTerm(searchTerm).toEither match {
            case Left(errors) =>
              scala.concurrent.Future.failed(ValidationError(errors.toList))
            case Right(validSearchTerm) =>
              ctx.ctx
                .runDbAction {
                  import profile.api._
                  UserAccountsDao
                    .filter(u =>
                      u.firstName.toLowerCase.like(s"%${validSearchTerm.toLowerCase}%") ||
                        u.lastName.toLowerCase.like(s"%${validSearchTerm.toLowerCase}%"))
                    .result
                }
                .recover { case ex: Exception =>
                  throw QueryError(
                    s"Failed to search user accounts: ${ex.getMessage}",
                    Some("SEARCH_USERS_FAILED"))
                }(ctx.ctx.ec)
          }
        }
      )
    )
  )
}

package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.resolvers

import io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao.UserAccountsComponent
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.ResolverContext
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.schema.TypeDefinitions
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
          ctx.ctx.runDbAction(UserAccountsDao.findById(id))
        }
      ),
      Field(
        "getUserAccounts",
        ListType(UserAccountType),
        description = Some("Get all user accounts"),
        resolve = ctx => {
          ctx.ctx.runDbAction(UserAccountsDao.findAll())
        }
      ),
      Field(
        "getUserAccountsByIds",
        ListType(UserAccountType),
        description = Some("Get multiple user accounts by IDs"),
        arguments = UserAccountIdsArg :: Nil,
        resolve = ctx => {
          val ids = ctx.arg(UserAccountIdsArg).asInstanceOf[Seq[String]]
          ctx.ctx.runDbAction(UserAccountsDao.findByIds(ids))
        }
      ),
      Field(
        "searchUserAccounts",
        ListType(UserAccountType),
        description = Some("Search user accounts by name"),
        arguments = Argument("searchTerm", StringType, description = "Search term for name") :: Nil,
        resolve = ctx => {
          val searchTerm = ctx.arg[String]("searchTerm")
          ctx.ctx.runDbAction {
            import profile.api._
            UserAccountsDao
              .filter(u => u.firstName.toLowerCase.like(s"%${searchTerm.toLowerCase}%") ||
                           u.lastName.toLowerCase.like(s"%${searchTerm.toLowerCase}%"))
              .result
          }
        }
      )
    )
  )
}
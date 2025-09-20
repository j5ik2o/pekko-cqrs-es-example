package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.resolvers

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.ResolverContext
import sangria.schema.ObjectType

trait MutationResolver {
  val MutationType: ObjectType[ResolverContext, Unit] = ???
}

package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.resolvers

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.ResolverContext
import sangria.schema.ObjectType

trait QueryResolver {
  val QueryType: ObjectType[ResolverContext, Unit] = ???
}

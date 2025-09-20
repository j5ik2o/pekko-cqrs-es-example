package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.resolvers

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.ResolverContext
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.schema.TypeDefinitions
import sangria.schema.*

/**
 * GraphQL Query リゾルバー（空実装）
 * CQRSパターンのためコマンド側では実装しない
 */
trait QueryResolver extends TypeDefinitions {
  
  val QueryType: ObjectType[ResolverContext, Unit] = ObjectType(
    "Query",
    "Root query type",
    fields[ResolverContext, Unit](
      Field(
        "_dummy",
        StringType,
        description = Some("Dummy field for GraphQL compliance"),
        resolve = _ => "This is a command-side API. Use the query-side API for data retrieval."
      )
    )
  )
}

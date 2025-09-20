package io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.schema

import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.ResolverContext
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.graphql.resolvers.{
  MutationResolver,
  QueryResolver
}
import sangria.schema.Schema

class GraphQLSchema extends TypeDefinitions with QueryResolver with MutationResolver {

  /**
   * GraphQLスキーマを生成
   *
   * @return
   *   完全なGraphQLスキーマ
   */
  def schema: Schema[ResolverContext, Unit] = Schema(
    query = QueryType,
    mutation = Some(MutationType),
    subscription = None // 将来的にSubscriptionResolverを追加
  )
}

object GraphQLSchema {

  /**
   * GraphQLスキーマインスタンスを生成
   *
   * @return
   *   GraphQLSchemaインスタンス
   */
  def apply(): GraphQLSchema = new GraphQLSchema()
}

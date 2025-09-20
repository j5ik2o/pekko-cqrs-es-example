package io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.schema

import io.github.j5ik2o.pcqrses.query.interfaceAdapter.dao.UserAccountsComponent
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.ResolverContext
import io.github.j5ik2o.pcqrses.query.interfaceAdapter.graphql.resolvers.QueryResolver
import sangria.schema.Schema
import slick.jdbc.JdbcProfile

/**
 * GraphQLスキーマを構築するためのファクトリー
 *
 * 型定義とリゾルバーを結合してSangriaのSchemaインスタンスを生成する。
 */
class GraphQLSchema(override val profile: JdbcProfile)
  extends UserAccountsComponent
  with TypeDefinitions
  with QueryResolver {

  /**
   * GraphQLスキーマを生成
   *
   * @return
   *   完全なGraphQLスキーマ
   */
  def schema: Schema[ResolverContext, Unit] = Schema(
    query = QueryType,
    mutation = None, // 将来的にMutationResolverを追加
    subscription = None // 将来的にSubscriptionResolverを追加
  )
}

object GraphQLSchema {

  /**
   * GraphQLスキーマインスタンスを生成
   *
   * @param profile
   *   JdbcProfile (例: PostgresProfile)
   * @return
   *   GraphQLSchemaインスタンス
   */
  def apply(profile: JdbcProfile): GraphQLSchema =
    new GraphQLSchema(profile)
}

package io.github.j5ik2o.pcqrses.command.interfaceAdapter.registry

/**
 * 汎用メッセージエクストラクター メッセージから集約IDとシャードIDを抽出
 *
 * @param numberOfShards
 * シャード数
 * @tparam ID
 * 集約IDの型
 * @tparam CMD
 * コマンドの型
 */
private class GenericShardingMessageExtractor[ID <: EntityId, CMD <: {def id: ID}](
                                                                                    numberOfShards: Int
                                                                                  ) extends org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor[
  ShardingEnvelope[CMD],
  CMD
] {

  override def entityId(envelope: ShardingEnvelope[CMD]): String =
    envelope.entityId

  override def shardId(entityId: String): String = {
    // エンティティIDのハッシュ値を使用してシャードIDを決定
    val shardNumber = math.abs(entityId.hashCode) % numberOfShards
    shardNumber.toString
  }

  override def unwrapMessage(envelope: ShardingEnvelope[CMD]): CMD =
    envelope.message
}

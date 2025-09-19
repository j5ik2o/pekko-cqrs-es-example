package io.github.j5ik2o.pcqrses.queryApi.config
import scala.jdk.DurationConverters.*
final case class ServerConfig(
  host: String,
  port: Int,
  shutdownTimeout: scala.concurrent.duration.FiniteDuration
)

object ServerConfig {
  def from(config: com.typesafe.config.Config): ServerConfig = {
    val queryApiconfig = config.getConfig("pcqrses.query-api")
    ServerConfig(
      host = queryApiconfig.getString("host"),
      port = queryApiconfig.getInt("port"),
      shutdownTimeout = queryApiconfig.getDuration("shutdown-timeout").toScala
    )
  }
}

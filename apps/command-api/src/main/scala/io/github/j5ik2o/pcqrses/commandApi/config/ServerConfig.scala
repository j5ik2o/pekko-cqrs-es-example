package io.github.j5ik2o.pcqrses.commandApi.config

import com.typesafe.config.Config

import scala.jdk.DurationConverters.*

final case class ServerConfig(
  host: String,
  port: Int,
  actorTimeout: scala.concurrent.duration.FiniteDuration,
  shutdownTimeout: scala.concurrent.duration.FiniteDuration
)

object ServerConfig {
  def from(config: Config): ServerConfig =
    ServerConfig(
      host = config.getString("server.host"),
      port = config.getInt("server.port"),
      actorTimeout = config.getDuration("actor-timeout").toScala,
      shutdownTimeout = config.getDuration("server.shutdown-timeout").toScala
    )
}

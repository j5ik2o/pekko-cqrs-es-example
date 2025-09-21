package io.github.j5ik2o.pcqrses.commandApi.config

import com.typesafe.config.Config

import scala.jdk.DurationConverters.*

final case class ServerConfig(
  host: String,
  port: Int,
  shutdownTimeout: scala.concurrent.duration.FiniteDuration
)

object ServerConfig {
  def from(config: Config): ServerConfig =
    ServerConfig(
      host = config.getString("host"),
      port = config.getInt("port"),
      shutdownTimeout = config.getDuration("shutdown-timeout").toScala
    )
}

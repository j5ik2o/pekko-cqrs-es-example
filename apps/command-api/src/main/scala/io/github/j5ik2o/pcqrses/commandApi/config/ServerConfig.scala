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
  def from(config: Config): ServerConfig = {
    val commandApiconfig = config.getConfig("pcqrses.command-api")
    ServerConfig(
      host = commandApiconfig.getString("host"),
      port = commandApiconfig.getInt("port"),
      actorTimeout = commandApiconfig.getDuration("actor-timeout").toScala,
      shutdownTimeout = commandApiconfig.getDuration("shutdown-timeout").toScala
    )
  }
}

package io.github.j5ik2o.pcqrses.commandApi.config

import scala.jdk.DurationConverters.*

final case class CommandApiConfig(
  actorTimeout: scala.concurrent.duration.FiniteDuration,
  server: ServerConfig,
  loadBalancerConfig: LoadBalancerConfig
                                 )

object CommandApiConfig {
  def from(config: com.typesafe.config.Config): CommandApiConfig =
    CommandApiConfig(
      actorTimeout = config.getDuration("actor-timeout").toScala,
      server = ServerConfig.from(config.getConfig("server")),
      loadBalancerConfig = LoadBalancerConfig.from(config.getConfig("load-balancer"))
    )
}

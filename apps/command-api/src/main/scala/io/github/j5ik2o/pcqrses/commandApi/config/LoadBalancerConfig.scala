package io.github.j5ik2o.pcqrses.commandApi.config

import com.typesafe.config.Config

import scala.jdk.DurationConverters.*

final case class LoadBalancerConfig(
                                     detachWaitDuration: scala.concurrent.duration.FiniteDuration,
                                     healthCheckGracePeriod: scala.concurrent.duration.FiniteDuration
                                   )

object LoadBalancerConfig {
  def from(config: Config): LoadBalancerConfig =
    LoadBalancerConfig(
      detachWaitDuration = config.getDuration("detach-wait-duration").toScala,
      healthCheckGracePeriod = config.getDuration("health-check-grace-period").toScala
    )
}

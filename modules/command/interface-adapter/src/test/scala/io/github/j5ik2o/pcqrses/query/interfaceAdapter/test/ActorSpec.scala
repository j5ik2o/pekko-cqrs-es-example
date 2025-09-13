package io.github.j5ik2o.pcqrses.query.interfaceAdapter.test

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.testkit.typed.TestKitSettings
import org.apache.pekko.actor.testkit.typed.scaladsl.{
  ActorTestKit,
  ActorTestKitBase,
  ScalaTestWithActorTestKit
}
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration.FiniteDuration

/**
 * Pekkoアクターテスト用の基底クラス
 *
 * テスト設定の共通化とヘルパーメソッドを提供
 */
abstract class ActorSpec(testKit: ActorTestKit)
  extends ScalaTestWithActorTestKit(testKit)
  with AnyFreeSpecLike
  with ScalaFutures
  with PatienceConfiguration {

  def testTimeFactor: Double = testKit.testKitSettings.TestTimeFactor

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = scaled(Span(15 * testTimeFactor, Seconds)),
      interval = scaled(Span(15 * testTimeFactor, Millis))
    )

  def this() = this(ActorTestKit(ActorTestKitBase.testNameFromCallStack()))

  def this(config: String) =
    this(
      ActorTestKit(
        ActorTestKitBase.testNameFromCallStack(),
        ConfigFactory.parseString(config)
      )
    )

  def this(config: Config) =
    this(ActorTestKit(ActorTestKitBase.testNameFromCallStack(), config))

  def this(config: Config, settings: TestKitSettings) =
    this(
      ActorTestKit(
        ActorTestKitBase.testNameFromCallStack(),
        config,
        settings
      )
    )

  implicit def classicSystem: ActorSystem = system.toClassic

  def killActors(actors: ActorRef[?]*)(maxDuration: FiniteDuration = timeout.duration): Unit =
    actors.foreach(actorRef => testKit.stop(actorRef, maxDuration))
}

package io.github.j5ik2o.pcqrses.readModelUpdater

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.serialization.SerializationExtension
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.duration.DurationLong

class LambdaHandler extends RequestHandler[DynamodbEvent, LambdaResponse] {
  LoggerFactory.getLogger(getClass)

  private val objectMapper = new ObjectMapper()
  objectMapper.registerModule(DefaultScalaModule)

  private val config = ConfigFactory.load()
  private lazy val system = ActorSystem("read-model-updater", config)
  SerializationExtension(system)

  config.getDuration("read-model-updater.timeouts.database-operation").getSeconds.seconds

  config.getDuration("read-model-updater.timeouts.system-termination").getSeconds.seconds
  private val databaseConfig: DatabaseConfig[JdbcProfile] =
    DatabaseConfig.forConfig[JdbcProfile]("read-model-updater.slick", config)
  databaseConfig.db
  val profile: slick.jdbc.JdbcProfile = databaseConfig.profile

  override def handleRequest(input: DynamodbEvent, context: Context): LambdaResponse = ???
}

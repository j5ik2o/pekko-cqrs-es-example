package io.github.j5ik2o.pcqrses.readModelUpdater

import com.fasterxml.jackson.databind.ObjectMapper
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishRequest

import java.net.URI
import scala.util.Try

/**
 * Read Model がこのシーケンス番号まで更新済みであることの通知。
 *
 * 通知は少なくとも1回届く。同じ通知が重なって届くことがあるので、受け手は (persistenceId, sequenceNr) で重複を除く。
 */
final case class ReadModelUpdated(persistenceId: String, sequenceNr: Long)

object ReadModelUpdated {
  private val mapper = new ObjectMapper()

  def toJson(notification: ReadModelUpdated): String = {
    val node = mapper.createObjectNode()
    node.put("type", "ReadModelUpdated")
    node.put("persistenceId", notification.persistenceId)
    node.put("sequenceNr", notification.sequenceNr)
    mapper.writeValueAsString(node)
  }
}

/** Read Model の更新を確定したあとに、外部へ通知を送る。 */
trait ReadModelUpdateNotifier {
  def publish(notification: ReadModelUpdated): Either[Throwable, Unit]
}

object ReadModelUpdateNotifier {

  /** 通知先を設定しない環境のためのもの。何も送らない。 */
  object Disabled extends ReadModelUpdateNotifier {
    override def publish(notification: ReadModelUpdated): Either[Throwable, Unit] = Right(())
  }

  def fromConfig(config: NotificationConfig): ReadModelUpdateNotifier =
    config.topic match {
      case None => Disabled
      case Some(topic) =>
        // Lambda のリージョンではなく、トピックのリージョンへ送る
        val builder = SnsClient
          .builder()
          .httpClient(UrlConnectionHttpClient.create())
          .region(Region.of(topic.region))
        config.endpoint.foreach(endpoint => builder.endpointOverride(URI.create(endpoint)))
        new SnsReadModelUpdateNotifier(builder.build(), topic.arn)
    }
}

/**
 * SNS トピックへ通知を送る。
 *
 * FIFO トピック（名前が `.fifo` で終わる）では、集約ごとの順序を保つため persistence id をメッセージグループにし、 再試行で同じ通知を重ねて送らないよう
 * `persistenceId:sequenceNr` を重複排除 ID にする。
 */
final class SnsReadModelUpdateNotifier(client: SnsClient, topicArn: String)
  extends ReadModelUpdateNotifier {

  private val fifo = topicArn.endsWith(".fifo")

  override def publish(notification: ReadModelUpdated): Either[Throwable, Unit] =
    Try {
      val request =
        PublishRequest.builder().topicArn(topicArn).message(ReadModelUpdated.toJson(notification))
      if (fifo) {
        request
          .messageGroupId(notification.persistenceId)
          .messageDeduplicationId(s"${notification.persistenceId}:${notification.sequenceNr}")
      }
      client.publish(request.build())
      ()
    }.toEither
}

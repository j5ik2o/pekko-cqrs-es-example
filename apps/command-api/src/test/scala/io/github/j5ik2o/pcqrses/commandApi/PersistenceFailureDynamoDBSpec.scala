package io.github.j5ik2o.pcqrses.commandApi

import com.github.j5ik2o.pekko.persistence.effector.scaladsl.{
  PersistEventFailedException,
  PersistenceId
}
import com.typesafe.config.{Config, ConfigFactory}
import io.github.j5ik2o.pcqrses.command.domain.users.*
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.aggregate.users.UserAccountAggregate
import io.github.j5ik2o.pcqrses.command.interfaceAdapter.contract.users.UserAccountProtocol.*
import org.apache.pekko.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior, ChildFailed, Terminated}
import org.scalatest.LoneElement
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, GetItemRequest, ScanRequest}

import java.net.URI
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

object PersistenceFailureDynamoDBSpec {

  /** DynamoDB Local でだけ流す。scripts/test-ppd-smoke.py が専用のテーブルを作って有効にする。 */
  val Enabled: Boolean = sys.env.get("PPD_EFFECTOR_IT").contains("true")

  // command-api の application.conf（j5ik2o.conf を含む）をそのまま使う。
  val config: Config = ConfigFactory
    .parseString("""
      pekko.actor.provider = local
      pekko.cluster.enabled = false
      """)
    .withFallback(ConfigFactory.load())

  /** ドメインの検証（256文字まで）を通らない名前。PPD の JournalHead 項目の上限 400KB を超える。 */
  def oversizedName(): UserAccountName = {
    val first = new FirstName { override val asString: String = "x" * 420000 }
    UserAccountName(first, LastName("Over"))
  }

  def name(first: String): UserAccountName = UserAccountName(FirstName(first), LastName("PPD"))

  sealed trait Outcome
  final case class Failed(cause: Throwable) extends Outcome
  case object StoppedWithoutFailure extends Outcome

  /** 集約の親の代わり。集約が落ちた原因を probe に送る。 */
  def observed(id: UserAccountId, outcomes: ActorRef[Outcome]): Behavior[Command] =
    Behaviors.setup { context =>
      val aggregate = context.spawnAnonymous(UserAccountAggregate(id))
      context.watch(aggregate)
      Behaviors
        .receiveMessage[Command] { command =>
          aggregate ! command
          Behaviors.same
        }
        .receiveSignal {
          case (_, ChildFailed(_, cause)) =>
            outcomes ! Failed(cause)
            Behaviors.stopped
          case (_, Terminated(_)) =>
            outcomes ! StoppedWithoutFailure
            Behaviors.stopped
        }
    }
}

/**
 * 公開 Effector と公開 PPD の組み合わせで、上限を超える書き込みが失敗したときの集約の振る舞いを確かめる。
 *
 * 集約・Effector の設定・シリアライザ・プラグインの設定は command-api のものを使う。上限超えだけは、ドメインの検証を通らない 名前を試験側で作って入れる。
 */
class PersistenceFailureDynamoDBSpec
  extends ScalaTestWithActorTestKit(PersistenceFailureDynamoDBSpec.config)
  with AnyFreeSpecLike
  with Matchers
  with LoneElement {
  import PersistenceFailureDynamoDBSpec.*

  private lazy val journalConfig = system.settings.config.getConfig("j5ik2o.dynamo-db-journal")
  private lazy val journalTable = journalConfig.getString("table-name")
  private lazy val headTable = journalConfig.getString("head.table-name")

  private lazy val dynamoDb: DynamoDbClient = DynamoDbClient
    .builder()
    .endpointOverride(URI.create(journalConfig.getString("dynamo-db-client.endpoint")))
    .region(Region.AP_NORTHEAST_1)
    .credentialsProvider(
      StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
    .build()

  override def withFixture(test: NoArgTest): org.scalatest.Outcome = {
    assume(Enabled, "set PPD_EFFECTOR_IT=true with a DynamoDB Local endpoint")
    super.withFixture(test)
  }

  override def afterAll(): Unit = {
    if (Enabled) dynamoDb.close()
    super.afterAll()
  }

  private def journalRows(pid: String): Vector[Map[String, AttributeValue]] =
    dynamoDb
      .scan(
        ScanRequest
          .builder()
          .tableName(journalTable)
          .consistentRead(true)
          .filterExpression("#pid = :pid")
          .expressionAttributeNames(Map("#pid" -> "persistence-id").asJava)
          .expressionAttributeValues(Map(":pid" -> AttributeValue.fromS(pid)).asJava)
          .build())
      .items()
      .asScala
      .toVector
      .map(_.asScala.toMap)
      .sortBy(_("sequence-nr").n().toLong)

  private def head(pid: String): Option[Map[String, AttributeValue]] =
    Option(
      dynamoDb
        .getItem(
          GetItemRequest
            .builder()
            .tableName(headTable)
            .consistentRead(true)
            .key(Map("persistence-id" -> AttributeValue.fromS(pid)).asJava)
            .build())
        .item())
      .filter(!_.isEmpty)
      .map(_.asScala.toMap)

  private def sequenceNrs(pid: String): Vector[Long] =
    journalRows(pid).map(_("sequence-nr").n().toLong)

  private def headSequenceNr(pid: String): Option[Long] =
    head(pid).map(_("sequence-nr").n().toLong)

  private def email(id: UserAccountId) = EmailAddress(s"${id.asString.toLowerCase}@example.com")

  private def spawnObserved(id: UserAccountId, outcomes: TestProbe[Outcome]) =
    spawn(observed(id, outcomes.ref))

  private def expectParentFailedWith(outcomes: TestProbe[Outcome], sequenceNr: Long): Unit =
    outcomes.expectMessageType[Outcome](10.seconds) match {
      case Failed(e: PersistEventFailedException) =>
        e.sequenceNr shouldBe sequenceNr
        e.persistenceId.asString should startWith("UserAccount-")
        e.getCause.getClass.getSimpleName shouldBe "JournalHeadItemTooLargeException"
      case other => fail(s"expected PersistEventFailedException, got $other")
    }

  "上限を超える書き込み" - {

    "最初の書き込みが失敗すると集約が落ち、何も書かれず、作り直した集約は番号1から保存する" in {
      val id = UserAccountId.generate()
      val pid = UserAccountAggregate.persistenceId(id).asString
      val outcomes = createTestProbe[Outcome]()
      val created = createTestProbe[CreateReply]()
      val got = createTestProbe[GetReply]()

      val parent = spawnObserved(id, outcomes)
      parent ! Create(id, oversizedName(), email(id), created.ref)
      // 永続化の応答を待つあいだに届いたコマンド。stash され、集約が落ちると捨てられる
      parent ! Get(id, got.ref)

      expectParentFailedWith(outcomes, sequenceNr = 1L)
      created.expectNoMessage(500.millis)
      got.expectNoMessage(500.millis)
      sequenceNrs(pid) shouldBe empty
      head(pid) shouldBe None

      val again = spawnObserved(id, createTestProbe[Outcome]())
      again ! Create(id, name("Recovered"), email(id), created.ref)
      created.expectMessage(10.seconds, CreateSucceeded(id))
      sequenceNrs(pid) shouldBe Vector(1L)
      headSequenceNr(pid) shouldBe Some(1L)
    }

    "保存済みの集約で失敗すると集約が落ち、既存の行は変わらず、作り直した集約は番号2から保存する" in {
      val id = UserAccountId.generate()
      val pid = UserAccountAggregate.persistenceId(id).asString
      val outcomes = createTestProbe[Outcome]()
      val created = createTestProbe[CreateReply]()
      val renamed = createTestProbe[RenameReply]()
      val got = createTestProbe[GetReply]()

      val parent = spawnObserved(id, outcomes)
      parent ! Create(id, name("Original"), email(id), created.ref)
      created.expectMessage(10.seconds, CreateSucceeded(id))
      val journalBefore = journalRows(pid)
      val headBefore = head(pid)

      parent ! Rename(id, oversizedName(), renamed.ref)
      parent ! Get(id, got.ref)

      expectParentFailedWith(outcomes, sequenceNr = 2L)
      renamed.expectNoMessage(500.millis)
      got.expectNoMessage(500.millis)
      journalRows(pid) shouldBe journalBefore
      head(pid) shouldBe headBefore

      val again = spawnObserved(id, createTestProbe[Outcome]())
      again ! Get(id, got.ref)
      got.expectMessageType[GetSucceeded](10.seconds).value.name shouldBe name("Original")
      again ! Rename(id, name("Renamed"), renamed.ref)
      renamed.expectMessage(10.seconds, RenameSucceeded(id))
      sequenceNrs(pid) shouldBe Vector(1L, 2L)
      headSequenceNr(pid) shouldBe Some(2L)
    }
  }

  "persistence id と区切り文字" - {

    "集約は UserAccount-<ULID> で保存し、PPD のキーは区切り文字 - で分けた値になる" in {
      val id = UserAccountId.generate()
      val pid = s"UserAccount-${id.asString}"
      val created = createTestProbe[CreateReply]()

      UserAccountAggregate.persistenceId(id).asString shouldBe pid
      spawnObserved(id, createTestProbe[Outcome]()) ! Create(
        id,
        name("Key"),
        email(id),
        created.ref)
      created.expectMessage(10.seconds, CreateSucceeded(id))

      val row = journalRows(pid).loneElement
      row("persistence-id").s() shouldBe pid
      row("pkey").s() should fullyMatch regex "UserAccount-\\d{40}"
      row("skey").s() shouldBe f"${id.asString}-${1L}%019d"
      headSequenceNr(pid) shouldBe Some(1L)
    }

    "entity id が区切り文字を含むと、Effector が保存の前に拒否する" in {
      val error = intercept[IllegalArgumentException] {
        PersistenceId.of(UserAccountId.EntityTypeName, "01JTEST-SUFFIX", "-")
      }
      error.getMessage should include("entityId cannot contain separator '-'")
      UserAccountId.parseFromString("01JTEST-SUFFIX0000000000000").isLeft shouldBe true
    }

    "Effector の既定の区切り文字 | を使うと、保存する id が RMU の接頭辞 UserAccount- と合わなくなる" in {
      val id = UserAccountId.generate()
      PersistenceId.of(UserAccountId.EntityTypeName, id.asString).asString shouldBe
        s"UserAccount|${id.asString}"
    }
  }
}

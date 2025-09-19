import Dependencies.*
import jp.co.septeni_original.sbt.dao.generator.model.ColumnDesc

import scala.sys.process.Process

val dbType = "postgresql"
val basename = "pcqrses"
val scala3Version = "3.6.2"
val dbName = s"${basename}_db"
val dbUserName = "postgres"
val dbPassword = "postgres"
val generatorPortNumber = 55432 // DAO生成専用ポート
lazy val generatorDatabaseUrl =
  s"jdbc:$dbType://localhost:$generatorPortNumber/$dbName?user=$dbUserName&password=$dbPassword"

ThisBuild / organization := "io.github.j5ik2o"
ThisBuild / scalaVersion := scala3Version
ThisBuild / version := "0.1.0"

ThisBuild / Test / fork := true
ThisBuild / Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
ThisBuild / Test / javaOptions += s"-Djacoco-agent.destfile=target/scala-${scalaVersion.value}/jacoco/data/jacoco.exec"

// Lint warning の回避: これらの設定は jacoco プラグインで使用される可能性があるため残しておく
Global / excludeLintKeys ++= Set(
  jacocoIncludes,
  jacocoExcludes,
  Docker / dockerBaseImage
)

ThisBuild / jacocoIncludes := Seq("*io.github.j5ik2o*")
ThisBuild / jacocoExcludes := Seq()

// 共通の依存関係
lazy val commonDependencies = Seq(
  logback.classic,
  slf4j.api,
  slf4j.julToSlf4J,
  scalatest.scalatest % Test
)

lazy val commonSettings = Seq(
  libraryDependencies ++= commonDependencies,
  scalacOptions ++= Seq(
    "-encoding",
    "utf8", // ソースファイルの文字コード指定
    "-feature", // 言語機能使用時に警告
    "-deprecation", // 非推奨API使用時に警告
    "-unchecked", // 型消去によって型安全が損なわれる場合に詳細情報
    "-source:3.4-migration", // 3.4へのマイグレーションモード
    "-language:implicitConversions", // 暗黙の型変換を許可
    "-language:higherKinds", // 高階型を許可
    "-language:postfixOps", // 後置演算子を許可
    "-explain", // コンパイルエラーと警告に詳細な説明を追加
    "-explain-types", // 型関連のエラーで詳細な型情報を表示
    "-Wunused:imports,privates", // 使用されていないインポートとプライベートメンバーに警告
    "-rewrite", // 書き換えを有効に
    "-no-indent", // インデント構文を拒否し、中括弧に変換
    "-experimental"
  ),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision
)

// Docker image common settings
// Keep images free of secrets; pass credentials at runtime via compose/env.
val dockerSettings = Seq(
  Docker / dockerBaseImage := "eclipse-temurin:17-jre-focal"
)

lazy val infrastructure = (project in file("modules/infrastructure"))
  .enablePlugins(JacocoPlugin)
  .settings(commonSettings)
  .settings(
    name := "infrastructure",
    libraryDependencies ++= Seq(
      circe.core,
      circe.generic,
      circe.parser,
      airframe.ulid,
      zio.core,
      zio.interopReactiveStreams
    )
  )

// command modules

lazy val commandDomain = (project in file("modules/command/domain"))
  .enablePlugins(JacocoPlugin)
  .settings(commonSettings)
  .settings(
    name := "command-domain",
    libraryDependencies ++= Seq(
      apache.commonsLang3
    )
  )
  .dependsOn(infrastructure)

lazy val commandInterfaceAdapterContract =
  (project in file("modules/command/interface-adapter-contract"))
    .enablePlugins(JacocoPlugin, PekkoGrpcPlugin)
    .settings(commonSettings)
    .settings(
      name := "command-interface-adapter-contract",
      libraryDependencies ++= Seq(apachePekko.actorTyped)
    )
    .dependsOn(commandDomain, infrastructure)

lazy val commandUseCase = (project in file("modules/command/use-case"))
  .enablePlugins(JacocoPlugin)
  .settings(commonSettings)
  .settings(
    name := "command-use-case",
    libraryDependencies ++= Seq(
      zio.core,
      zio.streams,
      zio.interopReactiveStreams,
      apachePekko.actorTestKitTyped % Test,
      scalatest.scalatest % Test,
      zio.test % Test,
      zio.testSbt % Test
    )
  )
  .dependsOn(
    commandDomain,
    commandInterfaceAdapterContract,
    infrastructure
  )

lazy val commandInterfaceAdapterEventSerializer =
  (project in file("modules/command/interface-adapter-event-serializer"))
    .enablePlugins(JacocoPlugin, PekkoGrpcPlugin)
    .settings(commonSettings)
    .settings(
      name := "command-interface-adapter-event-serializer",
      libraryDependencies ++= Seq(
        apachePekko.grpcRuntime,
        apachePekko.slf4j,
        apachePekko.serializationJackson,
        thesametScalapb.runtime % "protobuf",
        thesametScalapb.grpcRuntime
      ),
      Compile / pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Server),
      PB.protocExecutable := file(sys.env.getOrElse("PROTOC_PATH", "/usr/bin/protoc")),
      // 自動生成コードの警告を抑制（pekko-grpcディレクトリ内のみ）
      Compile / scalacOptions ++= Seq(
        "-Wconf:src=.*/target/.*pekko-grpc/.*:silent"
      )
    )
    .dependsOn(commandUseCase, commandInterfaceAdapterContract, infrastructure)

lazy val commandInterfaceAdapter = (project in file("modules/command/interface-adapter"))
  .enablePlugins(JacocoPlugin, PekkoGrpcPlugin)
  .settings(commonSettings)
  .settings(
    name := "command-interface-adapter",
    libraryDependencies ++= Seq(
      apachePekko.actorTyped,
      apachePekko.stream,
      zio.core,
      zio.prelude,
      zio.interopReactiveStreams,
      apachePekko.http,
      apachePekko.httpSprayJson,
      apachePekko.persistenceTyped,
      apachePekko.actorTestKitTyped % Test,
      apachePekko.persistenceTestkit % Test,
      apachePekko.discovery,
      apachePekko.clusterTyped,
      apachePekko.clusterShardingTyped,
      githubJ5ik2o.pekkoPersistenceEffector,
      googleapis.commonProtos
    ),
    Compile / pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Server),
    PB.protocExecutable := file(sys.env.getOrElse("PROTOC_PATH", "/usr/bin/protoc")),
    // 自動生成コードの警告を抑制（pekko-grpcディレクトリ内のみ）
    Compile / scalacOptions ++= Seq(
      "-Wconf:src=.*/target/.*pekko-grpc/.*:silent"
    )
  )
  .dependsOn(
    commandUseCase,
    commandInterfaceAdapterContract,
    commandInterfaceAdapterEventSerializer,
    infrastructure)

lazy val commandApi = (project in file("apps/command-api"))
  .enablePlugins(JacocoPlugin, JavaAppPackaging, DockerPlugin)
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(
    name := "command-api",
    Compile / mainClass := Some("io.github.j5ik2o.pcqrses.commandApi.MainActor"),
    Docker / packageName := s"${basename}-command-api",
    Docker / version := "0.1.0",
    Docker / dockerExposedPorts := Seq(18080),
    dockerBaseImage := "eclipse-temurin:17-jre-focal",
    libraryDependencies ++= Seq(
      zio.core
    )
  )
  .settings(
    libraryDependencies ++= Seq(
      githubJ5ik2o.pekkoPersistenceDynamoDBJournal,
      githubJ5ik2o.pekkoPersistenceDynamoDBSnapshot,
      apachePekko.clusterTyped,
      apachePekko.clusterShardingTyped,
      apachePekko.discovery,
      apachePekko.pekkoManagement,
      apachePekko.pekkoManagementClusterBootstrap,
      apachePekko.pekkoManagementClusterHttp
    )
  )
  .dependsOn(commandInterfaceAdapter, infrastructure)

// --- query modules

val TypeExtractor = ".*?/TYPE:(.*?)/.*".r

lazy val queryInterfaceAdapter = (project in file("modules/query/interface-adapter"))
  .enablePlugins(JacocoPlugin, SbtDaoGeneratorPlugin)
  .settings(commonSettings)
  .settings(
    name := "query-interface-adapter",
    libraryDependencies ++= Seq(
      apachePekko.actorTyped,
      apachePekko.stream,
      apachePekko.http,
      apachePekko.httpSprayJson,
      apachePekko.grpcRuntime,
      apachePekko.slf4j,
      sangria.sangria,
      sangria.sangriaCirce,
      pekkoHttpCirce.pekkoHttpCirce,
      slick.slick,
      slick.hikariCP,
      postgresql.postgresql
    ),
    generator / tableNameFilter := { tableName =>
      tableName.toUpperCase != "SCHEMA_VERSION" && tableName.toUpperCase != "FLYWAY_SCHEMA_HISTORY"
    },
    generator / advancedPropertyTypeNameMapper := {
      case (_, _, ColumnDesc(_, _, _, _, _, Some(TypeExtractor(t)), _)) => t.trim
      case (s, _, _) if s.toUpperCase() == "BIGINT" => "Long"
      case (s, _, _) if s.toUpperCase() == "INT4" => "Int"
      case (s, _, _) if s.toUpperCase() == "NUMERIC" => "Int"
      case (s, _, _) if s.toUpperCase() == "VARCHAR" => "String"
      case (s, _, _) if s.toUpperCase() == "BPCHAR" => "String" // CHAR型をStringに変換
      case (s, _, _) if s.toUpperCase() == "CHAR" => "String"
      case (s, _, _) if s.toUpperCase() == "TEXT" => "String"
      case (s, _, _) if s.toUpperCase() == "BOOLEAN" => "Boolean"
      case (s, _, _) if s.toUpperCase() == "BOOL" => "Boolean"
      case (s, _, _) if s.toUpperCase() == "DATE" => "java.time.LocalDate"
      case (s, _, _) if s.toUpperCase() == "TIMESTAMP" => "java.sql.Timestamp"
      case (s, _, _) if s.toUpperCase() == "TIMESTAMPTZ" => "java.sql.Timestamp"
      case (s, _, _) if s.toUpperCase() == "DECIMAL" => "BigDecimal"
      case (s, _, _) if s.toUpperCase() == "UUID" => "java.util.UUID"
      case (s, _, _) if s.toUpperCase() == "INT2" => "Short"
      case (s, _, _) if s.toUpperCase() == "INT8" => "Long"
      case (s, _, _) if s.toUpperCase() == "FLOAT4" => "Float"
      case (s, _, _) if s.toUpperCase() == "FLOAT8" => "Double"
      case (s, _, _) if s.toUpperCase() == "JSON" => "String"
      case (s, _, _) if s.toUpperCase() == "JSONB" => "String"
      case (s, _, _) => s
    },
    generator / templateDirectory := baseDirectory.value / "templates",
    generator / templateNameMapper := { (_: String) => "template.ftl" },
    generator / outputDirectoryMapper := { (_: String) =>
      (Compile / sourceDirectory).value / "scala" / "io" / "github" / "j5ik2o" / basename / "query" / "interfaceAdapter" / "dao"
    },
    // DAO生成用のJDBC設定（別ポート）
    generator / driverClassName := "org.postgresql.Driver",
    generator / jdbcUrl := generatorDatabaseUrl,
    generator / jdbcUser := dbUserName,
    generator / jdbcPassword := dbPassword,
    // PostgreSQL起動タスク（DAO生成用）
    TaskKey[Unit]("startPostgresForGenerate") := {
      Process(s"docker rm -f ${generatorDockerName}").!
      val cmd = List(
        "docker",
        "run",
        "--name",
        generatorDockerName,
        "-e",
        s"POSTGRES_USER=$dbUserName",
        "-e",
        s"POSTGRES_PASSWORD=$dbPassword",
        "-e",
        s"POSTGRES_DB=$dbName",
        "-p",
        s"${generatorPortNumber}:5432",
        "-d",
        "postgres:11.4"
      )
      streams.value.log.info(
        s"Starting PostgreSQL container: $generatorDockerName on port $generatorPortNumber")
      Process(cmd).!
      Thread.sleep(5000) // PostgreSQL起動待機
    },
    // PostgreSQL停止タスク（DAO生成用）
    TaskKey[Unit]("stopPostgresForGenerate") := {
      streams.value.log.info(s"Stopping PostgreSQL container: $generatorDockerName")
      Process(s"docker rm -f ${generatorDockerName}").!
    },
    // generateAllWithDbという新しいタスクを定義（PostgreSQL起動→マイグレーション→生成→PostgreSQL停止を順次実行）
    TaskKey[Seq[File]]("generateAllWithDb") := Def
      .sequential(
        TaskKey[Unit]("startPostgresForGenerate"),
        queryFlywayMigration / flywayMigrate,
        generator / generateAll,
        TaskKey[Unit]("stopPostgresForGenerate").map(_ => Seq.empty[File])
      )
      .value
  )
  .dependsOn(infrastructure)

val generatorDockerName = s"${basename}_postgres_generator" // DAO生成専用コンテナ名

lazy val queryFlywayMigration = (project in file("modules/query/flyway-migration"))
  .enablePlugins(FlywayPlugin, JacocoPlugin)
  .settings(commonSettings)
  .settings(
    name := "query-flyway-migration",
    libraryDependencies += "org.postgresql" % "postgresql" % "42.7.4",
    // 開発環境のポート（50504）を使用
    flywayUrl := generatorDatabaseUrl,
    flywayDriver := "org.postgresql.Driver",
    flywayLocations := Seq("classpath:db/migration"),
    flywayBaselineOnMigrate := true,
    flywayCleanDisabled := false
  )
  .dependsOn(infrastructure)

lazy val queryApi = (project in file("apps/query-api"))
  .enablePlugins(JacocoPlugin, JavaAppPackaging, DockerPlugin)
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(
    name := "query-api",
    Compile / mainClass := Some(s"io.github.j5ik2o.${basename}.queryApi.MainActor"),
    Docker / packageName := s"${basename}-query-api",
    Docker / version := "0.1.0",
    Docker / dockerExposedPorts := Seq(18082),
    dockerBaseImage := "eclipse-temurin:17-jre-focal",
    libraryDependencies += "org.apache.pekko" %% "pekko-discovery" % Version.pekko
  )
  .dependsOn(queryInterfaceAdapter, infrastructure)

// Read Model Updater
lazy val readModelUpdater = (project in file("apps/read-model-updater"))
  .enablePlugins(JacocoPlugin, JavaAppPackaging, DockerPlugin)
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(
    name := "read-model-updater",
    Compile / mainClass := Some(s"io.github.j5ik2o.${basename}.readModelUpdater.Main"),
    // Docker設定
    Docker / packageName := s"${basename}-read-model-updater",
    Docker / version := "0.1.0",
    dockerBaseImage := "eclipse-temurin:17-jre-focal",
    // AWS Lambda用の設定（仮）
    // FIXME: インフラストラクチャーに設定ファイルを置くのはよくない…。あとで直す
    Universal / mappings += file(
      "modules/infrastructure/src/main/resources/application.conf") -> "conf/application.conf",
    Universal / javaOptions ++= Seq("-Dconfig.file=conf/application.conf"),
    assembly / assemblyJarName := "read-model-updater-lambda.jar",
    assembly / mainClass := Some(s"io.github.j5ik2o.${basename}.readModelUpdater.LambdaHandler"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case PathList("reference.conf") => MergeStrategy.concat
      case PathList("application.conf") => MergeStrategy.concat
      case x if x.endsWith(".proto") => MergeStrategy.first
      case x if x.endsWith(".class") => MergeStrategy.first
      case x if x.endsWith(".properties") => MergeStrategy.concat
      case x if x.endsWith(".xml") => MergeStrategy.first
      case x if x.endsWith(".txt") => MergeStrategy.concat
      case x if x.endsWith(".dtd") => MergeStrategy.first
      case x if x.endsWith(".xsd") => MergeStrategy.first
      case x if x.contains("module-info") => MergeStrategy.discard
      case _ => MergeStrategy.first
    },
    assembly / assemblyExcludedJars := {
      val cp = (assembly / fullClasspath).value
      cp filter { f =>
        val name = f.data.getName.toLowerCase
        // Scala compiler関連
        name.contains("scala-compiler") ||
        name.contains("scala-reflect") ||
        (name.contains("scalap") && !name.contains("scalapb")) ||
        name.contains("scala-xml") ||
        name.contains("scala-parser") ||
        // Pekko Serializationは保持、それ以外のPekkoモジュールは除外
        name.contains("pekko-http") ||
        name.contains("pekko-cluster") ||
        // pekko-remoteとpekko-streamは必要なので除外しない
        // name.contains("pekko-remote") ||
        // name.contains("pekko-stream") ||
        name.contains("pekko-management") ||
        name.contains("pekko-discovery") ||
        name.contains("pekko-coordination") ||
        name.contains("pekko-persistence-dynamodb") ||
        name.contains("pekko-persistence-query") ||
        name.contains("pekko-persistence-journal") ||
        name.contains("pekko-persistence-snapshot") ||
        name.contains("pekko-testkit") ||
        name.contains("pekko-slf4j") ||
        // pekko-protobuf-v3は必要なので除外しない
        (name.contains("pekko-protobuf") && !name.contains("pekko-protobuf-v3")) ||
        // 大きくて不要な一部ライブラリのみ除外（DB関連は含める）
        name.contains("flyway") ||
        // テスト関連は除外
        name.contains("scalatest") ||
        name.contains("scalacheck") ||
        name.contains("jacoco") ||
        // その他の大きなライブラリ
        name.contains("netty") ||
        name.contains("grpc-netty") ||
        name.contains("grpc-core") ||
        name.contains("grpc-api") ||
        name.contains("grpc-context") ||
        name.contains("swagger") ||
        name.contains("unused") ||
        name.contains("zio-") ||
        name.contains("cats-") ||
        name.contains("shapeless")
      }
    },
    libraryDependencies ++= Seq(
      awsLambda.core,
      awsLambda.events,
      awsSdkV2.dynamodb,
      jackson.databind,
      jackson.moduleScala,
      logback.classic,
      slf4j.api,
      thesametScalapb.runtime,
      thesametScalapb.grpcRuntime,
      apachePekko.actorTyped,
      apachePekko.persistenceTyped,
      apachePekko.serializationJackson,
      apachePekko.remote,
      apachePekko.stream,
      apachePekko.protobufV3
    )
  )
  .dependsOn(commandDomain, commandInterfaceAdapterEventSerializer, queryInterfaceAdapter)

lazy val root = (project in file("."))
  .enablePlugins(JacocoPlugin)
  .settings(
    name := s"${basename}-root",
    jacocoAggregateReportSettings := JacocoReportSettings(
      title = basename,
      formats = Seq(JacocoReportFormats.ScalaHTML)
    )
  )
  .aggregate(
    commandDomain,
    commandUseCase,
    commandInterfaceAdapter,
    commandApi,
    queryInterfaceAdapter,
    queryFlywayMigration,
    queryApi,
    readModelUpdater
  )
  .settings(commonSettings)

addCommandAlias("lint", ";scalafmtCheck;test:scalafmtCheck;scalafmtSbtCheck;scalafixAll --check")
addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt;scalafix RemoveUnused")
addCommandAlias("testCoverage", ";test;jacocoAggregateReport")

addCommandAlias("migrateQuery", "queryFlywayMigration/flywayMigrate")
addCommandAlias("infoQuery", "queryFlywayMigration/flywayInfo")
addCommandAlias("validateQuery", "queryFlywayMigration/flywayValidate")
addCommandAlias(
  "cleanMigrateQuery",
  ";queryFlywayMigration/flywayClean;queryFlywayMigration/flywayMigrate")

addCommandAlias("dockerBuildAll", ";commandApi/docker:publishLocal;queryApi/docker:publishLocal")

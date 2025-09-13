import sbt._

object Version {
  val scalaTest = "3.2.12"
  val pekko = "1.1.3"
  val pekkoHttp = "1.2.0"
  val pekkoGrpc = "1.1.1"

  val actorTyped = "1.1.3"
  val httpSprayJson = "1.2.0"
  val slf4jVersion = "1.7.36"

  val managementVersion = "1.1.1" // Pekko Management最新安定版
}

object Dependencies {
  object apachePekko {
    val slf4j = "org.apache.pekko" %% "pekko-slf4j" % Version.pekko

    val actorTyped = "org.apache.pekko" %% "pekko-actor-typed" % Version.pekko
    val actorTestKitTyped = "org.apache.pekko" %% "pekko-actor-testkit-typed" % Version.pekko

    val stream = "org.apache.pekko" %% "pekko-stream" % Version.pekko
    val streamTestKit = "org.apache.pekko" %% "pekko-stream-testkit" % Version.pekko

    val persistenceTyped = "org.apache.pekko" %% "pekko-persistence-typed" % Version.pekko
    val persistenceTestkit = "org.apache.pekko" %% "pekko-persistence-testkit" % Version.pekko

    val serializationJackson = "org.apache.pekko" %% "pekko-serialization-jackson" % Version.pekko

    val http = "org.apache.pekko" %% "pekko-http" % Version.pekkoHttp
    val httpTestKit = "org.apache.pekko" %% "pekko-http-testkit" % Version.pekkoHttp
    val httpSprayJson = "org.apache.pekko" %% "pekko-http-spray-json" % Version.httpSprayJson
    val grpcRuntime = "org.apache.pekko" %% "pekko-grpc-runtime" % Version.pekkoGrpc

    val clusterTyped = "org.apache.pekko" %% "pekko-cluster-typed" % Version.pekko
    val clusterShardingTyped = "org.apache.pekko" %% "pekko-cluster-sharding-typed" % Version.pekko

    val discovery = "org.apache.pekko" %% "pekko-discovery" % Version.pekko
    val remote = "org.apache.pekko" %% "pekko-remote" % Version.pekko
    val protobufV3 = "org.apache.pekko" %% "pekko-protobuf-v3" % Version.pekko
    val pekkoManagement = "org.apache.pekko" %% "pekko-management" % Version.managementVersion
    val pekkoManagementClusterBootstrap =
      "org.apache.pekko" %% "pekko-management-cluster-bootstrap" % Version.managementVersion
    val pekkoManagementClusterHttp =
      "org.apache.pekko" %% "pekko-management-cluster-http" % Version.managementVersion
  }

  object logback {
    val classic = "ch.qos.logback" % "logback-classic" % "1.3.14"
  }

  object slf4j {
    val api = "org.slf4j" % "slf4j-api" % Version.slf4jVersion
    val julToSlf4J = "org.slf4j" % "jul-to-slf4j" % Version.slf4jVersion
  }

  object fasterxml {
    val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.18.2"
  }

  object postgresql {
    val postgresql = "org.postgresql" % "postgresql" % "42.7.5"
  }

  object thesametScalapb {
    val runtime =
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion
    val grpcRuntime =
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion

  }

  object googleapis {
    val commonProtos =
      "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % "2.9.6-0"
  }

  object scalameta {
    val munit = "org.scalameta" %% "munit" % "1.0.0"
  }

  object scalatest {
    val scalatest = "org.scalatest" %% "scalatest" % Version.scalaTest
  }

  object githubJ5ik2o {
    val pekkoPersistenceDynamoDBJournal =
      "io.github.j5ik2o" %% s"pekko-persistence-dynamodb-journal-v2" % "1.0.57"
    val pekkoPersistenceDynamoDBSnapshot =
      "io.github.j5ik2o" %% s"pekko-persistence-dynamodb-snapshot-v2" % "1.0.57"
    val pekkoPersistenceEffector = "io.github.j5ik2o" %% "pekko-persistence-effector" % "0.8.11"
  }

  object circe {
    val core = "io.circe" %% "circe-core" % "0.14.12"
    val generic = "io.circe" %% "circe-generic" % "0.14.12"
    val parser = "io.circe" %% "circe-parser" % "0.14.12"
  }

  object airframe {
    val ulid = "org.wvlet.airframe" %% "airframe-ulid" % "2025.1.8"
  }

  object apache {
    val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.17.0"
  }

  object typelevel {
    val catsCore = "org.typelevel" %% "cats-core" % "2.12.0"
    val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.7"
    val catsEffectKernel = "org.typelevel" %% "cats-effect-kernel" % "3.5.7"
    val catsEffectStd = "org.typelevel" %% "cats-effect-std" % "3.5.7"
    val catsEffectTesting = "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0"
  }

  object zio {
    val core = "dev.zio" %% "zio" % "2.0.21"
    val streams = "dev.zio" %% "zio-streams" % "2.0.21"
    val test = "dev.zio" %% "zio-test" % "2.0.21"
    val testSbt = "dev.zio" %% "zio-test-sbt" % "2.0.21"
    val interopReactiveStreams = "dev.zio" %% "zio-interop-reactivestreams" % "2.0.2"
    val prelude = "dev.zio" %% "zio-prelude" % "1.0.0-RC21"
  }

  object sangria {
    val sangria = "org.sangria-graphql" %% "sangria" % "4.2.4"
    val sangriaCirce = "org.sangria-graphql" %% "sangria-circe" % "1.3.2"
  }

  object pekkoHttpCirce {
    val pekkoHttpCirce = "com.github.pjfanning" %% "pekko-http-circe" % "3.2.2"
  }

  object slick {
    val slick = "com.typesafe.slick" %% "slick" % "3.6.1"
    val hikariCP = "com.typesafe.slick" %% "slick-hikaricp" % "3.6.1"
  }

  object kinesis {
    // https://mvnrepository.com/artifact/software.amazon.kinesis/amazon-kinesis-client
    val client = "software.amazon.kinesis" % "amazon-kinesis-client" % "3.0.3"
  }

  object awsLambda {
    val core = "com.amazonaws" % "aws-lambda-java-core" % "1.2.3"
    val events = "com.amazonaws" % "aws-lambda-java-events" % "3.11.6"
  }

  object awsSdkV2 {
    val dynamodb = "software.amazon.awssdk" % "dynamodb" % "2.20.162"
  }

  object jackson {
    val databind = "com.fasterxml.jackson.core" % "jackson-databind" % "2.18.2"
    val moduleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.18.2"
  }
}

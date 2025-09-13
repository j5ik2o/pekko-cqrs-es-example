// Database migration
// https://github.com/flyway/flyway-sbt
addSbtPlugin("com.github.sbt" % "flyway-sbt" % "10.21.0")

// Native Packager
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.3")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.7")

addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.1.1")

// この対応を行わないと、環境変数を読み込むことができない
// https://github.com/Philippus/sbt-dotenv?tab=readme-ov-file#illegal-reflective-access-warnings-and-exceptions
addSbtPlugin("nl.gn0s1s" % "sbt-dotenv" % "3.1.1")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.2")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.4")

addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.5.0")

// Assembly plugin for creating fat JARs
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")

// DAO Generator - 最後に読み込む
addSbtPlugin(
  ("io.github.sbt-dao-generator" % "sbt-dao-generator" % "1.4.1")
    .exclude("org.scalameta", "scalafmt-dynamic_2.12"))

libraryDependencies ++= Seq(
  "org.flywaydb" % "flyway-database-postgresql" % "10.21.0",
  "com.thesamet.scalapb" %% "compilerplugin" % "0.11.17"
)

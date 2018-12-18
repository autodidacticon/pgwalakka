name := "walakka"
organization := "io.walakka"
version := "0.1"

scalaVersion := "2.12.8"

lazy val akkaVersion = "2.5.19"

lazy val postgresVersion = "42.2.5"

lazy val slickVersion = "3.2.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe" % "config" % "1.3.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-codegen" % slickVersion,
  "org.postgresql" % "postgresql" % postgresVersion,
  "org.scalikejdbc" %% "scalikejdbc" % "3.3.1",
  "org.antlr"     % "antlr4"     % "4.7.1",
  "org.json4s" %% "json4s-native" % "3.6.3",
  "org.json4s" %% "json4s-jackson" % "3.6.3",
  "org.slf4j" % "slf4j-log4j12" % "1.7.10",
) ++ testDependencies

lazy val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "org.mockito" % "mockito-core" % "2.23.4" % "test"
)

antlr4PackageName in Antlr4 := Some("io.walakka.postgres.replication.logicaldecoding")

enablePlugins(Antlr4Plugin)

enablePlugins(FlywayPlugin)

flywayUrl := "jdbc:postgresql://localhost:5432/postgres"
flywayUser := "postgres"
flywayPassword := "postgres"
flywayLocations += "db/migration"
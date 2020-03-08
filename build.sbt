
name := "parent"
organization := "io.walakka"
version := "0.1"

scalaVersion := "2.12.8"

lazy val akkaVersion = "2.6.3"

lazy val postgresVersion = "42.2.5"

lazy val slickVersion = "3.2.3"

lazy val kafkaClientVersion = "2.1.1"

lazy val root = (project in file("."))
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
        "com.typesafe.akka" %% "akka-stream" % akkaVersion,
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
        "com.typesafe" % "config" % "1.3.3",
        "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
        "org.slf4j" % "slf4j-log4j12" % "1.7.10",
      ) ++ testDependencies
    )

lazy val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  "org.scalatestplus" %% "scalatestplus-mockito" % "1.0.0-M2" % Test,
  "org.mockito" % "mockito-core" % "3.3.0" % Test
)

lazy val core = (project in file("core"))
  .dependsOn(root % "compile->compile;test->test")

lazy val postgres = (project in file("postgres"))
  .enablePlugins(Antlr4Plugin, FlywayPlugin)
  .settings(
    antlr4PackageName in Antlr4 := Some("io.walakka.postgres.replication.logicaldecoding"),
    libraryDependencies ++= Seq(
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-codegen" % slickVersion,
      "org.postgresql" % "postgresql" % postgresVersion,
      "org.scalikejdbc" %% "scalikejdbc" % "3.3.1",
      "org.antlr" % "antlr4" % "4.7.1",
      "org.json4s" %% "json4s-native" % "3.6.3",
      "org.json4s" %% "json4s-jackson" % "3.6.3",
    ),
    flywayUrl := "jdbc:postgresql://localhost:5432/postgres",
    flywayUser := "postgres",
    flywayPassword := "postgres",
    flywayLocations += "src/main/resources/migration"
  )
  .dependsOn(core)

lazy val kafka = (project in file("kafka"))
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % kafkaClientVersion
    )
  )

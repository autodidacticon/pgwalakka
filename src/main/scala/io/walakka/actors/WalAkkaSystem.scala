package io.walakka.actors

import java.sql.DriverManager
import java.util.Properties

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.walakka.postgres.replication.DbMsg
import io.walakka.producers.{DummyProducer, OutputProducer, OutputResult}
import org.postgresql.replication.LogSequenceNumber
import org.postgresql.{PGConnection, PGProperty}

import scala.concurrent.duration._

trait WalAkkaSystem {
  val actorSystem: ActorSystem

  implicit val timeout = Timeout(5 seconds)

  def createManagerActor(
                          replicationOptions: ReplicationOptions,
                          managerName: String,
                          cnxnFun: () => PGConnection = getConnection,
                          outputProducer: OutputProducer[LogSequenceNumber, DbMsg, OutputResult] = new DummyProducer {}
                        ): ActorRef =
    actorSystem.actorOf(ManagerActor
      .props(replicationOptions, cnxnFun, outputProducer),
      managerName)

  val getConnection = () => {

    val config = ConfigFactory.load()
    val props = new Properties()
    PGProperty.USER.set(props, config.getString("user"))
    PGProperty.PASSWORD.set(props, config.getString("password"))
    PGProperty.ASSUME_MIN_SERVER_VERSION.set(props, config.getString("version"))
    PGProperty.REPLICATION.set(props, "database")
    PGProperty.PREFER_QUERY_MODE.set(props, "simple")

    DriverManager.getConnection(config.getString("db.jdbcUrl"), props).unwrap(classOf[PGConnection])
  }
}

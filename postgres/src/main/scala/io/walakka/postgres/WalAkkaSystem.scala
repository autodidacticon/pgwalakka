package io.walakka.postgres

import java.sql.DriverManager
import java.util.Properties

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.postgresql.{PGConnection, PGProperty}

trait WalAkkaSystem {
  val actorSystem: ActorSystem

  val getConnection = () => {

    val config = ConfigFactory.load()
    val props = new Properties()
    PGProperty.USER.set(props, config.getString("db.user"))
    PGProperty.PASSWORD.set(props, config.getString("db.password"))
    PGProperty.ASSUME_MIN_SERVER_VERSION.set(props, config.getString("db.version"))
    PGProperty.REPLICATION.set(props, "database")
    PGProperty.PREFER_QUERY_MODE.set(props, "simple")

    DriverManager.getConnection(config.getString("db.jdbcUrl"), props).unwrap(classOf[PGConnection])
  }
}

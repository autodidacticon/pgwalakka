package io.walakka

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.walakka.actors.ActorManager
import io.walakka.actors.ReplicationActor.StartProcessing
import io.walakka.actors.ReplicationActorManagerActor.ReplicationStatus
import io.walakka.postgres.replication.{ActiveSlot, ReplicationManager}

import scala.collection.JavaConverters._

object WalAkka extends App with ReplicationManager with ActorManager with LazyLogging {
  val actorSystemName = "walakka"
  val actorSystem = ActorSystem(actorSystemName)
  val slotOptions: Map[String, String] = ConfigFactory.load()
    .getObject("replication").unwrapped().asScala.map{case(k,v) => k.toString -> v.toString}.toMap

  val replicationThreshold = ConfigFactory.load().getLong("walakka.threshold")

  val statusIntervalMs = ConfigFactory.load().getInt("walakka.statusInterval")

  val activeSlots: Seq[ActiveSlot] = {
    val curSlots: Seq[ActiveSlot] = getReplicationSlotsSync
    curSlots match {
      case e: Seq[ActiveSlot] if e.isEmpty => {
        logger.info("Creating initial replication slot")
        createReplicationSlot("walakka")
        getReplicationSlotsSync
      }
      case e => e
    }
  }

  val mgr = createManagerActor

  //create replication actors
  mgr ! activeSlots

  run

  def run: Unit = {
    mgr ! ReplicationStatus
    Thread.sleep(statusIntervalMs / 10)
    run
  }

}

package io.walakka

import akka.actor.ActorSystem
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.walakka.actors.{ReplicationOptions, WalAkkaSystem}
import io.walakka.actors.ManagerActor.ReplicationStatus
import io.walakka.postgres.replication.{ActiveSlot, ReplicationManager}
import io.walakka.producers.DummyProducer

import scala.collection.JavaConverters._

object WalAkka
  extends App
    with ReplicationManager
    with WalAkkaSystem
    with LazyLogging {
  val actorSystemName = "walakka"
  val actorSystem = ActorSystem(actorSystemName)
  val slotOptions: Map[String, String] = ConfigFactory
    .load()
    .getObject("replication")
    .unwrapped()
    .asScala
    .map { case (k, v) => k.toString -> v.toString }
    .toMap

  val replicationThreshold = ConfigFactory.load().getLong("walakka.threshold")

  val statusIntervalMs = ConfigFactory.load().getInt("walakka.statusInterval")

  val maxSlots = ConfigFactory.load().getInt("postgres.maxWalSenders")

  val activeSlots: Seq[ActiveSlot] = {
    val curSlots: Seq[ActiveSlot] = getReplicationSlotsSync
    curSlots match {
      case e: Seq[ActiveSlot] if e.isEmpty => {
        logger.info("Creating initial replication slot")
        val name = this.getClass.getName
        createReplicationSlot(name)
        insertCatchupLsn(name)
        getReplicationSlotsSync
      }
      //if all slots are catchup slots, then create a new 'leader' slot
      case e if e.filter(_.catchupLsn.isEmpty).isEmpty => {
        logger.info("Creating leader replication slot")
        createReplicationSlot()
          .map(activeSlot => {
            insertCatchupLsn(activeSlot.slotName)
            getReplicationSlotsSync
          })
          .getOrElse(Seq.empty)
      }
      case e => e
    }
  }

  val replicationOptions = new ReplicationOptions(replicationThreshold,
    slotOptions,
    statusIntervalMs,
    maxSlots)

  val mgr = createManagerActor(replicationOptions, actorSystemName, getConnection, new DummyProducer {})

  //create replication actors
  activeSlots.foreach(mgr ! _)

  run

  def run: Unit = {
    Thread.sleep(statusIntervalMs)
    mgr ? ReplicationStatus
    run
  }

}

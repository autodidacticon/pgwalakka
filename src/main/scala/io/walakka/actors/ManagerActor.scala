package io.walakka.actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.walakka.actors.ManagerActor.StartProcessing
import io.walakka.actors.PgReplicationActor.{StopProcessing, StreamStatusRequest, StreamStatusResponse}
import io.walakka.postgres.replication.{ActiveSlot, DbMsg, ReplicationManager, SlotStatus}
import io.walakka.producers.{OutputProducer, OutputResult}
import org.postgresql.PGConnection
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}

import scala.concurrent.duration._

class ManagerActor(
                   val replicationOptions: ReplicationOptions,
                   val getConnection: () => PGConnection,
                   val producer: OutputProducer[LogSequenceNumber, DbMsg, OutputResult]
                  )
  extends Actor
    with LazyLogging
    with ReplicationManager {

  implicit val timeout = Timeout(5 seconds)

  implicit val ec = context.dispatcher

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2, withinTimeRange = 5 seconds) {
    case _: RuntimeException =>
      sender ! PgReplicationActor.StartProcessing
      Restart
  }

  override def receive: Receive = {
    case slot: ActiveSlot => {
      logger.info(
        s"Creating actor for slot ${slot.slotName}")
      val (cnxn, stream) = getConnectionAndStream(slot.slotName)
      createReplicationActor(
        slot,
        cnxn,
        stream
        ) ! StartProcessing
      sender() ! slot
    }
    case StartProcessing(slotName) => {
      context.child(slotName)
        .map(actorRef => (actorRef ? PgReplicationActor.StartProcessing).mapTo[StreamStatusResponse])
        .foreach(_.map(sender() ! _))
    }
    case PgReplicationActor.StreamStatusResponse(streamIsClosed)
      if streamIsClosed => {
      logger.info(s"Stopping actor ${sender().path.name} with closed stream")
      sender() ! PgReplicationActor.StopProcessing
    }
    case ManagerActor.ReplicationStatus => checkReplicationStatus
    case ManagerActor.AssignCatchupSlot(slotName) => {
      //create a new replication slot
      createReplicationSlot()
        .map {
          case slot: ActiveSlot =>
            //create record for new slot
            insertCatchupLsn(slot.slotName)
            slot.catchupLsn.foreach(xlog => logger.warn(
              s"Creating catchup slot / actor for $slotName at lsn: $xlog"))
            updateCatchupLsn(slotName, slot.catchupLsn)
            //assign actor to newly created replication slot and start processing
            val (cnxn, stream) = getConnectionAndStream(slot.slotName)
            createReplicationActor(slot,
              cnxn,
              stream
            ) ! PgReplicationActor.StartProcessing
        }
        .getOrElse(logger.warn(
          s"Max replication slots have been created but slot $slotName has fallen behind."))
    }
    case ManagerActor.TerminateActor(name) => terminateReplicationActor(name)
    case Terminated(actorRef) => {

      val (caughtUp, behind) = getReplicationSlotStatusSync(actorRef.path.name)
        .partition(isCaughtUp)

      caughtUp.foreach(slot => {
        dropReplicationSlot(slot.slotName)
        removeCatchupLsn(sender().path.name)
      })

      behind.foreach(
        slot => {
          val (cnxn, stream) = getConnectionAndStream(slot.slotName)
          createReplicationActor(ActiveSlot(slot.slotName),
            cnxn,
            stream)
        })
    }
  }

  def createReplicationActor(
                              slot: ActiveSlot,
                              cnxn: PGConnection,
                              stream: PGReplicationStream
                            ): ActorRef =
    context.watch(
      context.actorOf(
        PgReplicationActor.props(cnxn, stream, producer),
        slot.slotName))

  def checkReplicationStatus = {
    logger.info("Interrogating slot status")
    //get replication slots
    val (activeSlots, inactiveSlots) =
      getReplicationSlotStatusSync.partition(_.isActive)

    val (slotsWithNoActor, slotsWithInactiveActors) =
      inactiveSlots.partition(slot => actorForSlot(slot.slotName).isEmpty)

    //recreate actors for empty slots
    self ! slotsWithNoActor.map(slot =>
      ActiveSlot(slot.slotName, slot.catchupLsn))

    slotsWithInactiveActors
      .flatMap(slot => actorForSlot(slot.slotName))
      .map(_ ! StopProcessing)

    //request active slot statuses
    activeSlots
      .flatMap(slot => actorForSlot(slot.slotName))
      .foreach(_ ! StreamStatusRequest)

    // slots that have recently fallen behind will not have a catchupLsn assigned
    // get slots with
    val (unassigned, assigned) =
    activeSlots.partition(_.catchupLsn.isEmpty)

    unassigned
      .filter(exceedsThreshold(_))
      .foreach(slotStatus =>
        self ! ManagerActor.AssignCatchupSlot(slotStatus.slotName))
    assigned
      .filter(isCaughtUp)
      .foreach(
        slot =>
          //terminate actor assigned to the slot
          terminateReplicationActor(slot.slotName))
  }

  def actorForSlot(slotName: String) = context.child(slotName)

  def exceedsThreshold(
                        slotStatus: SlotStatus,
                        threshold: Long = replicationOptions.replicationThreshold): Boolean =
    slotStatus.walDist.map(_ > threshold).getOrElse(false)

  def isCaughtUp(slotStatus: SlotStatus): Boolean =
    slotStatus.catchupDist.map(_ <= 0).getOrElse(false)

  def terminateReplicationActor(slotName: String) =
    context
      .child(slotName)
      .map(r => {
        logger.warn(s"Terminating actor $slotName")
        r ! PgReplicationActor.StopProcessing
      })
}

object ManagerActor {
  def props(replOpts: ReplicationOptions,
            cnxnFun: () => PGConnection,
            outputProducer: OutputProducer[LogSequenceNumber, DbMsg, OutputResult]): Props =
    Props(new ManagerActor(replOpts, cnxnFun, outputProducer))

  case object ReplicationStatus

  case class TerminateActor(slotName: String)

  case class AssignCatchupSlot(slotName: String)

  case class StartProcessing(slotName: String)

}

case class ActorTerminated(actorRef: ActorRef)

package io.walakka.actors

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.walakka.postgres.replication.{ActiveSlot, ReplicationManager, SlotStatus, TestDecodingUtil}
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ReplicationActor(val slotName: String, val stream: PGReplicationStream)
                      (implicit val ec: ExecutionContext = ExecutionContext.global)
    extends Actor
    with TestDecodingUtil
    with LazyLogging {


  private var processing: Boolean = false

  def decodeBytes(byteBuffer: ByteBuffer) =
    StandardCharsets.UTF_8.decode(byteBuffer).toString

  def msgSource: Stream[String] =
    Stream.continually(stream.read).map(decodeBytes)

  def processWal: Unit = {
    msgSource
      .map(parseMsg)
      //filter out DDL stmts which do not produce DbMsg
      .flatMap(p => p)
      .foreach(db => {
        logger.debug(db.toString)
        stream.setFlushedLSN(stream.getLastReceiveLSN)
        Thread.sleep(1000)
      })
  }

  override def receive: Receive = {
    case ReplicationActor.StartProcessing =>
      processing match {
        case true => sender() ! ReplicationActor.Processing
        case false => {
          Future {
            processing = true
            processWal
          }
          sender() ! ReplicationActor.Processing
        }
      }
  }
}

object ReplicationActor {
  def props(slotName: String, stream: PGReplicationStream) =
    Props(new ReplicationActor(slotName, stream))

  case object StartProcessing

  case object Processing
}

class ManagerActor(val replOpts: ReplicationOptions, val supervisorOpt: Option[ActorRef] = None)
    extends Actor
    with LazyLogging
    with ReplicationManager {

  val statusIntervalMs = replOpts.statusIntervalMs

  implicit val timeout = Timeout(5 seconds)

  implicit val ec = context.dispatcher

  override def receive: Receive = {
    case slots: Seq[ActiveSlot] if slots.nonEmpty => {
      logger.info(
        s"Creating actors for slots ${slots.map(_.slotName).mkString(";")}")
      slots
        .map(
          slot =>
            slot.slotName -> createReplicationActor(
              slot,
              createReplicationStream(slot.slotName, replOpts.slotOptions)))
        .foreach {
          case (_, actorRef) => actorRef ! ReplicationActor.StartProcessing
        }
    }

    case ManagerActor.ReplicationStatus => checkReplicationStatus
    case ManagerActor.TerminateActor(name) => terminateReplicationActor(name)
    case Terminated(actorRef) => {
      supervisorOpt.foreach(ref => ref ! ActorTerminated(actorRef))
      //drop the replication slot
      dropReplicationSlot(actorRef.path.name)
      //remove the catchup record
      removeCatchupLsn(actorRef.path.name)
    }
  }

  def createReplicationActor(slot: ActiveSlot,
                             replicationStream: PGReplicationStream): ActorRef =
    context.watch(context.actorOf(ReplicationActor.props(slot.slotName, replicationStream),
                    slot.slotName))

  def restartActors(slots: Seq[SlotStatus]): Seq[ActorRef] = {
    slots.map(slot => {
      logger.info(s"Recreating actor for ${slot.slotName}")
      terminateReplicationActor(slot.slotName)
      createReplicationActor(ActiveSlot(slot.slotName), createReplicationStream(slot.slotName, replOpts.slotOptions))
    })
  }

  def checkReplicationStatus = {
    logger.info("Interrogating slot status")
    //get replication slots
    val slots = getReplicationSlotStatusSync
    //get any slots whose actor has died and resume processing
    restartActors(slots.filterNot(_.isActive))
    //get any slots that have exceeded the configured threshhold
    val behind = slots.filter(exceedsThreshold(_, replOpts.replicationThreshold))
    // slots that have recently fallen behind will not have a catchupLsn assigned
    val (unassigned, assigned) =
      behind.partition(slot => slot.catchupLsn.isEmpty)
    unassigned.foreach(slot => {
      if (slots.length < replOpts.maxSlots) {
        //create a new replication slot
        val (catchupSlot, xlog) =
          createReplicationSlot()
        //create record for new slot
        insertCatchupLsn(catchupSlot.slotName)
        logger.warn(s"Creating catchup slot / actor for ${slot.slotName} at lsn: $xlog")
        updateCatchupLsn(slot.slotName, Some(xlog))
        //assign actor to newly created replication slot and start processing
        createReplicationActor(catchupSlot,
                               createReplicationStream(
                                 catchupSlot.slotName,
                                 replOpts.slotOptions)) ! ReplicationActor.StartProcessing
      } else {
        logger.warn(
          s"Max replication slots have been created but slot ${slot.slotName} has fallen behind.")
      }
    })
    assigned
      .filter(isCaughtUp)
      .foreach(slot => {
        logger.warn(s"Terminating slot / actor ${slot.slotName}")
        //terminate actor assigned to the slot
        terminateReplicationActor(slot.slotName)
      })
  }

  def exceedsThreshold(slotStatus: SlotStatus, threshold: Long): Boolean =
    slotStatus.walDist.map(_ > threshold).getOrElse(false)

  def isCaughtUp(slotStatus: SlotStatus): Boolean =
    slotStatus.catchupDist.map(_ <= 0).getOrElse(true)

  def terminateReplicationActor(slotName: String) =
    context.child(slotName).map(r => {
     Await.result(r ? PoisonPill, Duration.Inf)
    })
}

object ManagerActor {
  def props(replOpts: ReplicationOptions, actoRefOpt: Option[ActorRef] = None) =
    Props(new ManagerActor(replOpts, actoRefOpt))

  case object ReplicationStatus

  case class TerminateActor(slotName: String)

}

case class ReplicationActorStatus(slotName: String,
                                  curLsn: LogSequenceNumber,
                                  terminateLsn: Option[LogSequenceNumber])

case class ReplicationOptions(replicationThreshold: Long,
                              slotOptions: Map[String, String],
                              statusIntervalMs: Int,
                              maxSlots: Int)

case class ActorTerminated(actorRef: ActorRef)

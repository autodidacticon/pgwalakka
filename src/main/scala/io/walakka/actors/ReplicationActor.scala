package io.walakka.actors

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import com.typesafe.scalalogging.LazyLogging
import io.walakka.postgres.replication.{ActiveSlot, ReplicationManager, SlotStatus, TestDecodingUtil}
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}

import scala.concurrent.{ExecutionContext, Future}

class ReplicationActor(val slotName: String, val stream: PGReplicationStream) extends Actor with TestDecodingUtil with LazyLogging {
  implicit val ec = ExecutionContext.global

  private var processing: Boolean = false

  def decodeBytes(byteBuffer: ByteBuffer) = StandardCharsets.UTF_8.decode(byteBuffer).toString

  def msgSource: Stream[String] = Stream.continually(stream.read).map(decodeBytes)

  def processWal: Unit = {
    msgSource.map(parseMsg)
      //filter out DDL stmts which do not produce DbMsg
      .flatMap(p => p)
      .foreach(db => {
        logger.info(db.toString)
        stream.setFlushedLSN(stream.getLastReceiveLSN)
      })
  }

  override def receive: Receive = {
    case ReplicationActor.StartProcessing => processing match {
      case true => sender() ! ReplicationActor.Processing
      case false => {
        Future {
          processing = true
          processWal
        }
        sender() ! ReplicationActor.Processing
      }
    }
    case ReplicationActor.StreamStatus => sender() ! stream.isClosed
  }
}

object ReplicationActor {
  def props(slotName: String, stream: PGReplicationStream) =
    Props(new ReplicationActor(slotName, stream))

  case object StartProcessing

  case object StreamStatus

  case object Processing
}

class ReplicationActorManagerActor(val replicationThreshold: Long, val slotOptions: Map[String, String], val statusIntervalMs: Int) extends Actor with LazyLogging with ReplicationManager {

  override def receive: Receive = {
    case slots: Seq[ActiveSlot] => {
      logger.info(s"Creating actors for slots ${slots.map(_.slotName).mkString(";")}")
      slots.map(slot => slot.slotName -> createReplicationActor(slot, createReplicationStream(slot.slotName, slotOptions)))
        .foreach{case(_, actorRef) => actorRef ! ReplicationActor.StartProcessing}
    }
    case ReplicationActorManagerActor.ReplicationStatus => checkStatus
  }

  def createReplicationActor(slot: ActiveSlot, replicationStream: PGReplicationStream): ActorRef =
    context.actorOf(ReplicationActor.props(slot.slotName, replicationStream))

  def checkStatus = {
    //get replication slots that exceed the threshold and do not have a catchup lsn assigned
    val slots = getReplicationSlotStatusSync
    val (behind, caughtup) = slots.partition(exceedsThreshold(_, replicationThreshold))
    val (unassigned, assigned) = behind.partition(slot => slot.catchupLsn.isEmpty)
    unassigned.foreach(slot => {
      logger.warn(s"Creating catchup slot / actor for ${slot.slotName}")
      //insert catchup record for existing slot
      insertCatchupLsn(slot.slotName, slot.walCurrent)
      val catchupSlot = createReplicationSlot(slot.slotName + s"${slots.length}")
      //create new replication slot at the head of the wal assign actor to it
      createReplicationActor(catchupSlot, createReplicationStream(catchupSlot.slotName, slotOptions)) ! ReplicationActor.StartProcessing
    })
    assigned.filter(isCaughtUp).foreach(slot => {
      logger.info(s"Terminating slot / actor ${slot.slotName}")
      //terminate actor assigned to the slot
      terminateReplicationActor(slot)
      //remove the catchup record
      removeCatchupLsn(slot.slotName)
      //drop the replication slot
      dropReplicationSlot(slot.slotName)
    })
  }

  def isActorReplicationStreamHealthy(actor: ActorRef) = actor ! ReplicationActor.StreamStatus

  def exceedsThreshold(slotStatus: SlotStatus, threshold: Long): Boolean = slotStatus.walDist > threshold

  def isCaughtUp(slotStatus: SlotStatus): Boolean = slotStatus.catchupDist.map(_ <= 0).getOrElse(false)

  def terminateReplicationActor(slotStatus: SlotStatus) = context.actorSelection(s"./${slotStatus.slotName}") ! PoisonPill
}

object ReplicationActorManagerActor {
  case object ReplicationStatus
}

case class ReplicationActorStatus(slotName: String, curLsn: LogSequenceNumber, terminateLsn: Option[LogSequenceNumber])
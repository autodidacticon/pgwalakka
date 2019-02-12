package io.walakka.actors

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.sql.Connection

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.walakka.actors.ReplicationActor.{StopProcessing, StreamStatus}
import io.walakka.postgres.replication._
import org.postgresql.PGConnection
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class ReplicationActor(val slotName: String, val cnxn: PGConnection, val streamFun: PGConnection => PGReplicationStream)
                      (implicit val ec: ExecutionContext = ExecutionContext.global)
  extends Actor
    with TestDecodingUtil
    with LazyLogging {

  def decodeBytes(byteBuffer: ByteBuffer) =
    StandardCharsets.UTF_8.decode(byteBuffer).toString

  lazy val processWal = {
    source
      //filter out DDL stmts which do not produce DbMsg
      .via(killSwitch.flow)
      .map { case (bytes, lsn) => decodeBytes(bytes) -> lsn }
      .map { case (dbMsg, lsn) => parseMsg(dbMsg) -> lsn }
      .runFoldAsync(()) {
        case (_, (dbMsgOpt, lsn)) => Future {
          logger.debug(dbMsgOpt.toString)
          stream.setFlushedLSN(lsn)
          stream.setAppliedLSN(lsn)
        }
      }(ActorMaterializer())
  }

  val killSwitch = KillSwitches.shared(slotName)

  lazy val stream = streamFun(cnxn)

  lazy val source = Source.unfoldAsync(None)(_ => Future(Some(None, stream.read() -> stream.getLastReceiveLSN)))

  lazy val sink = Sink.foreach[(Option[DbMsg], LogSequenceNumber)] { case (dbMsgOpt, lsn) => {
    logger.debug(dbMsgOpt.toString)
    stream.setFlushedLSN(lsn)
    stream.setAppliedLSN(lsn)
  }
  }

  override def receive: Receive = {
    case ReplicationActor.StartProcessing => processWal
    case ReplicationActor.StopProcessing => {
      stream.close()
      killSwitch.shutdown()
      context.stop(self)
    }
    case ReplicationActor.StreamStatus => sender() ! StreamStatus(stream.isClosed)
  }

  override def postStop(): Unit = cnxn.asInstanceOf[Connection].close
}

object ReplicationActor {
  def props(slotName: String, cnxn: PGConnection, stream: PGConnection => PGReplicationStream) =
    Props(new ReplicationActor(slotName, cnxn, stream))

  case object StartProcessing

  case object Processing

  case object StreamStatus

  case class StreamStatus(streamIsClosed: Boolean)

  case object StopProcessing

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
              getConnection,
              createReplicationStream(slot.slotName, replOpts.slotOptions, statusIntervalMs)))
        .foreach {
          case (_, actorRef) => actorRef ! ReplicationActor.StartProcessing
        }
    }
    case ReplicationActor.StreamStatus(streamIsClosed)
      if streamIsClosed => {
      logger.info(s"Stopping actor ${sender().path.name} with closed stream")
      sender() ! ReplicationActor.StopProcessing
    }
    case ManagerActor.ReplicationStatus => checkReplicationStatus
    case ManagerActor.TerminateActor(name) => terminateReplicationActor(name)
    case Terminated(actorRef) => {
      supervisorOpt.foreach(ref => ref ! ActorTerminated(actorRef))
      val (caughtUp, behind) = getReplicationSlotStatusSync(actorRef.path.name)
        .partition(isCaughtUp)

      caughtUp.foreach(slot => {
        dropReplicationSlot(slot.slotName)
        removeCatchupLsn(sender().path.name)
      })

      behind.foreach(slot => createReplicationActor(ActiveSlot(slot.slotName), getConnection, createReplicationStream(slot.slotName, replOpts.slotOptions, statusIntervalMs)))
    }
  }

  def createReplicationActor(slot: ActiveSlot,
                             cnxn: PGConnection,
                             replicationStream: PGConnection => PGReplicationStream): ActorRef =
    context.watch(context.actorOf(ReplicationActor.props(slot.slotName, cnxn, replicationStream),
      slot.slotName))

  def checkReplicationStatus = {
    logger.info("Interrogating slot status")
    //get replication slots
    val (activeSlots, inactiveSlots) = getReplicationSlotStatusSync.partition(_.isActive)

    val (slotsWithNoActor, slotsWithInactiveActors) = inactiveSlots.partition(slot => actorForSlot(slot.slotName).isEmpty)

    //recreate actors for empty slots
    slotsWithNoActor.map(slot => createReplicationActor(ActiveSlot(slot.slotName, slot.catchupLsn),
      getConnection,
      createReplicationStream(slot.slotName, replOpts.slotOptions)
    ))

    slotsWithInactiveActors.flatMap(slot => actorForSlot(slot.slotName)).map(_ ! StopProcessing)

    //get any slots whose stream has closed and resume processing
    activeSlots
      .flatMap(slot => actorForSlot(slot.slotName))
      .foreach(_ ! StreamStatus)

    // slots that have recently fallen behind will not have a catchupLsn assigned
    val (unassigned, assigned) =
      activeSlots.partition(slot => slot.catchupLsn.isEmpty)

    unassigned.filter(exceedsThreshold(_))
      .foreach(slot => {
        if (activeSlots.length < replOpts.maxSlots) {
          //create a new replication slot
          val (catchupSlot, xlog) = createReplicationSlot()
          //create record for new slot
          insertCatchupLsn(catchupSlot.slotName)
          logger.warn(s"Creating catchup slot / actor for ${slot.slotName} at lsn: $xlog")
          updateCatchupLsn(slot.slotName, Some(xlog))
          //assign actor to newly created replication slot and start processing
          createReplicationActor(catchupSlot,
            getConnection,
            createReplicationStream(
              catchupSlot.slotName,
              replOpts.slotOptions,
              statusIntervalMs
            )) ! ReplicationActor.StartProcessing
        } else {
          logger.warn(
            s"Max replication slots have been created but slot ${slot.slotName} has fallen behind.")
        }
      })
    assigned
      .filter(isCaughtUp)
      .foreach(slot => {
        //terminate actor assigned to the slot
        terminateReplicationActor(slot.slotName)
      })
  }

  def actorForSlot(slotName: String) = context.child(slotName)

  def exceedsThreshold(slotStatus: SlotStatus, threshold: Long = replOpts.replicationThreshold): Boolean =
    slotStatus.walDist.map(_ > threshold).getOrElse(false)

  def isCaughtUp(slotStatus: SlotStatus): Boolean =
    slotStatus.catchupDist.map(_ <= 0).getOrElse(false)

  def terminateReplicationActor(slotName: String) =
    context.child(slotName).map(r => {
      logger.warn(s"Terminating actor $slotName")
      r ! ReplicationActor.StopProcessing
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

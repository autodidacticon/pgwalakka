package io.walakka.actors

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.sql.Connection

import akka.NotUsed
import akka.actor.{Actor, Props}
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import io.walakka.postgres.replication._
import io.walakka.producers.{OutputProducer, OutputResult}
import org.postgresql.PGConnection
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}

import scala.concurrent.ExecutionContext

class PgReplicationActor(
                          val connection: PGConnection,
                          val stream: PGReplicationStream,
                          val producer: OutputProducer[LogSequenceNumber, DbMsg, OutputResult])
  extends Actor
    with TestDecodingUtil
    with LazyLogging {

  implicit val ec: ExecutionContext = context.dispatcher

  def decodeBytes(byteBuffer: ByteBuffer) =
    StandardCharsets.UTF_8.decode(byteBuffer).toString

  lazy val processWal = {
    source
      .map { case (bytes, lsn) => decodeBytes(bytes) -> lsn }
      .map { case (dbMsg, lsn) => parseMsg(dbMsg) -> lsn }
      //filter out DDL stmts which do not produce DbMsg
      .filter{ case (dbMsgOpt, _) => dbMsgOpt.isDefined }
      .runWith(sink)(ActorMaterializer())
  }

  lazy val source: Source[(ByteBuffer, LogSequenceNumber), NotUsed] =
    Source.repeat(NotUsed).map(_ => stream.read() -> stream.getLastReceiveLSN)

  lazy val sink = Sink.foreach[(Option[DbMsg], LogSequenceNumber)] {
    case (dbMsgOpt, lsn) => {
      logger.debug(dbMsgOpt.toString)
      producer.put(lsn, dbMsgOpt.get)
        .map(_ => {
          logger.debug(s"Flushing LSN: ${lsn.toString}")
          stream.setFlushedLSN(lsn)
          stream.setAppliedLSN(lsn)
        })
    }
  }

  override def receive: Receive = {
    case PgReplicationActor.StartProcessing => {
      processWal
    }

    case PgReplicationActor.StreamStatusRequest => sender() ! PgReplicationActor.StreamStatusResponse(stream.isClosed)

    case PgReplicationActor.StopProcessing => {
      context.stop(self)
    }
  }
}

object PgReplicationActor {
  def props(cnxn: PGConnection,
            stream: PGReplicationStream,
            producer: OutputProducer[LogSequenceNumber, DbMsg, OutputResult]) =
    Props(new PgReplicationActor(cnxn, stream, producer))

  case object StartProcessing

  case object StreamStatusRequest

  case class StreamStatusResponse(streamIsClosed: Boolean)

  case object StopProcessing

}

case class ReplicationActorStatus(slotName: String,
                                  curLsn: LogSequenceNumber,
                                  terminateLsn: Option[LogSequenceNumber])

case class ReplicationOptions(replicationThreshold: Long,
                              slotOptions: Map[String, String],
                              statusIntervalMs: Int,
                              maxSlots: Int)

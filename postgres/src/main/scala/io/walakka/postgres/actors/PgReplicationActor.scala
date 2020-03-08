package io.walakka.postgres.actors

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.LazyLogging
import io.walakka.actors.Replication.ReplicationSlot
import io.walakka.actors.ReplicationActor
import io.walakka.actors.ReplicationActor.{ReplicationAction, Start}
import io.walakka.postgres.OutputResult
import io.walakka.postgres.replication.{DbMsg, TestDecodingUtil}
import io.walakka.producers.OutputProducer
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}

import scala.concurrent.ExecutionContext

class PgReplicationSlot(
                          name: String,
                          val stream: PGReplicationStream,
                          val producer: OutputProducer[LogSequenceNumber, DbMsg, OutputResult])
                       (implicit ec: ExecutionContext)
  extends ReplicationSlot(name)
    with TestDecodingUtil
    with LazyLogging {

  val decodeBytes = (byteBuffer: ByteBuffer) =>
    StandardCharsets.UTF_8.decode(byteBuffer).toString

  val processWal = (s: PGReplicationStream) => {
    for {
      msg <- Some(decodeBytes(s.read()))
      dbMsg <- parseMsg(msg)
      } yield dbMsg -> s.getLastReceiveLSN
  }: Option[(DbMsg, LogSequenceNumber)]

  val processorFactory = (mat: Materializer) =>
    Source.unfoldResource[(DbMsg, LogSequenceNumber), PGReplicationStream](() => stream, processWal, _.close())
      .runForeach{
        case (dbMsgOpt, lsn) => {
          logger.debug(dbMsgOpt.toString)
          producer.put(lsn, dbMsgOpt)
            .foreach(_ => {
              logger.debug(s"Flushing LSN: ${lsn.toString}")
              stream.setFlushedLSN(lsn)
              stream.setAppliedLSN(lsn)
            })
        }
      }(mat)

  override def createBehavior: Behavior[ReplicationActor.ReplicationAction] = Behaviors receivePartial [ReplicationAction] {
    case (context, Start) =>
      processorFactory(Materializer.createMaterializer(context))
      Behaviors.same
  }
}

case class ReplicationOptions(replicationThreshold: Long,
                              slotOptions: Map[String, String],
                              statusIntervalMs: Int,
                              maxSlots: Int)

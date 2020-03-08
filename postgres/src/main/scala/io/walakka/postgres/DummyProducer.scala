package io.walakka.postgres

import com.typesafe.scalalogging.LazyLogging
import io.walakka.postgres.replication.DbMsg
import io.walakka.producers.OutputProducer
import org.postgresql.replication.LogSequenceNumber

import scala.concurrent.{ExecutionContext, Future}

trait DummyProducer
  extends OutputProducer[LogSequenceNumber, DbMsg, OutputResult]
    with LazyLogging {
  override def put(lsn: LogSequenceNumber, dbMsg: DbMsg)(
    implicit ec: ExecutionContext): Future[OutputResult] = Future {
    logger.debug(dbMsg.toString)
    OutputResult(dbMsg)
  }
}

case class OutputResult(dbMsg: DbMsg, metadata: Map[String, String] = Map.empty)

package io.walakka.producers

import com.typesafe.scalalogging.LazyLogging
import io.walakka.postgres.replication.DbMsg
import org.postgresql.replication.LogSequenceNumber

import scala.concurrent.{ExecutionContext, Future}

trait OutputProducer[K, V, R] {
  def put(key: K, value: V)(implicit ec: ExecutionContext): Future[R]
}

trait DummyProducer
    extends OutputProducer[LogSequenceNumber, DbMsg, OutputResult]
    with LazyLogging {
  override def put(lsn: LogSequenceNumber, dbMsg: DbMsg)(
      implicit ec: ExecutionContext): Future[OutputResult] = Future {
    logger.debug(dbMsg.toString)
    OutputResult(dbMsg)
  }
}

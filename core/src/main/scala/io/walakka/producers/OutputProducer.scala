package io.walakka.producers

import scala.concurrent.{ExecutionContext, Future}

trait OutputProducer[K, V, R] {
  def put(key: K, value: V)(implicit ec: ExecutionContext): Future[R]
}


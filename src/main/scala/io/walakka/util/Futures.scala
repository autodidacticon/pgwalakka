package io.walakka.util

import java.util.concurrent.{CompletableFuture, Future => JavaFuture}

import scala.compat.java8.FutureConverters
import scala.concurrent.Future

object FutureConverter {

  implicit class JFutures[R](jf: JavaFuture[R]) {
    def asScala: Future[R] =
      FutureConverters.toScala(CompletableFuture.supplyAsync(() => jf.get))
  }

}

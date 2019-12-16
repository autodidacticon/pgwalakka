package io.walakka.producers

import io.walakka.postgres.replication.DbMsg

case class OutputResult(dbMsg: DbMsg, metadata: Map[String, String] = Map.empty)

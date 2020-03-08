package io.walakka.postgres.replication

import io.walakka.actors.Replication.ReplicationSlot
import org.postgresql.replication.LogSequenceNumber

case class PgReplicationSlot(override val name: String, lsnOpt: Option[LogSequenceNumber]) extends ReplicationSlot(name)

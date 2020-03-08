package io.walakka.postgres.replication

import java.sql.Timestamp
import java.util.concurrent.TimeUnit

import io.walakka.actors.SlotStatus
import io.walakka.postgres.actors.ReplicationOptions
import io.walakka.postgres.models.Tables.{SlotCatchup, SlotCatchupRow}
import org.postgresql.PGConnection
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

trait Db {
  lazy val db = Database.forConfig("src/main/resources/db")

  def getTxTsFromTxId(txId: String): Timestamp = {
    Await.result(
      db.run(sql"select pg_xact_commit_timestamp($txId)".as[Timestamp].head),
      Duration.Inf)
  }

  def runSync[T](expr: DBIO[T], duration: Duration = Duration.Inf): T =
    Await.result(db.run(expr), duration)
}

trait ReplicationManager extends Db {

  val replicationOptions: ReplicationOptions

  val getConnection: () => PGConnection

  def createReplicationStream(slotName: String): PGReplicationStream = createReplicationStream(slotName, getConnection(), replicationOptions)

  def createReplicationStream(slotName: String, connection: PGConnection, slotOptions: ReplicationOptions = replicationOptions): PGReplicationStream = {
    //use an anonymous connection instance to pass with the replication stream
    val builder = connection.getReplicationAPI
      .replicationStream()
      .logical()
      .withSlotName(slotName)
      .withStatusInterval(replicationOptions.statusIntervalMs, TimeUnit.MILLISECONDS)
    replicationOptions.slotOptions
      .foldLeft(builder) {
        case (builder, t) => builder.withSlotOption(t._1, t._2)
      }
      .start()
  }

  def generateSlotName( inputName: Int = 0): String = "walakka_" + inputName

  def createPgReplicationSlot(slotNumber: Int): Try[PgReplicationSlot] = createPgReplicationSlot(generateSlotName(slotNumber))

  def createPgReplicationSlot(slotName: String = generateSlotName(),
                            outputPlugin: String = "test_decoding")
  : Try[PgReplicationSlot] = Try {
    val (_, lsn) = runSync(
      sql"select * from pg_create_logical_replication_slot($slotName, $outputPlugin)"
        .as[(String, String)]
        .head)

    PgReplicationSlot(slotName, Some(LogSequenceNumber.valueOf(lsn)))
  }

  def dropPgReplicationSlot(slotName: String): Int =
    runSync(sql"select 1 from pg_drop_replication_slot($slotName)".as[Int].head)

  def getPgReplicationSlotDist(slotName: String): DBIO[Int] = {
    sql"select pg_xlog_location_diff(pg_current_xlog_insert_location(), restart_lsn) from pg_replication_slots where slot_name = $slotName"
      .as[Int]
      .head
  }

  def getPgReplicationSlots: DBIO[Seq[PgReplicationSlot]] = {
    implicit val getPgReplicationSlot = GetResult(
      r =>
        PgReplicationSlot(r.nextString(),
          r.nextStringOption().map(LogSequenceNumber.valueOf)))
    sql"select prs.slot_name, sc.catchup_lsn from walakka.slot_catchup sc left join pg_replication_slots prs on prs.slot_name = sc.slot_name where prs.slot_name like 'walakka%'"
      .as[PgReplicationSlot]
  }

  def getPgReplicationSlotsSync = runSync(getPgReplicationSlots)

  def getPgReplicationSlotStatus: DBIO[Seq[PgReplicationSlotStatus]] = {
    implicit val getSlotStatus = GetResult(
      r =>
        PgReplicationSlotStatus(r.nextString(),
          r.nextBoolean(),
          r.nextLongOption(),
          r.nextLongOption(),
          r.nextStringOption().map(LogSequenceNumber.valueOf)))
    sql"""select sc.slot_name,
          coalesce(prs.active, false) as is_active,
         pg_xlog_location_diff(pg_current_xlog_insert_location(), prs.confirmed_flush_lsn) as wal_dist,
         pg_xlog_location_diff(sc.catchup_lsn :: pg_lsn, prs.confirmed_flush_lsn) as catchup_dist,
         sc.catchup_lsn
         from walakka.slot_catchup sc left join pg_replication_slots prs on prs.slot_name = sc.slot_name"""
      .as[PgReplicationSlotStatus]
  }

  def getPgReplicationSlotStatusSync: Seq[PgReplicationSlotStatus] =
    runSync(getPgReplicationSlotStatus)

  def getPgReplicationSlotStatus(slotName: String): DBIO[Option[PgReplicationSlotStatus]] = {
    implicit val getSlotStatus = GetResult(
      r =>
        PgReplicationSlotStatus(r.nextString(),
          r.nextBoolean(),
          r.nextLongOption(),
          r.nextLongOption(),
          r.nextStringOption().map(LogSequenceNumber.valueOf)))
    sql"""select sc.slot_name,
          coalesce(prs.active, false) as is_active,
         pg_xlog_location_diff(pg_current_xlog_insert_location(), prs.confirmed_flush_lsn) as wal_dist,
         pg_xlog_location_diff(sc.catchup_lsn :: pg_lsn, prs.confirmed_flush_lsn) as catchup_dist,
         sc.catchup_lsn
         from walakka.slot_catchup sc left join pg_replication_slots prs on prs.slot_name = sc.slot_name
         where sc.slot_name = $slotName
      """
      .as[PgReplicationSlotStatus]
      .headOption
  }

  def getPgReplicationSlotStatusSync(slotName: String): Option[PgReplicationSlotStatus] =
    runSync(getPgReplicationSlotStatus(slotName))

  def removeCatchupLsn(slotName: String): Int =
    runSync(SlotCatchup.filter(_.slotName === slotName).delete)

  def insertCatchupLsn(slotName: String,
                       catchupLsn: Option[LogSequenceNumber] = None) =
    runSync({
      DBIO.seq(
        SlotCatchup += SlotCatchupRow(slotName, catchupLsn.map(_.asString())))
    })

  def updateCatchupLsn(slotName: String,
                       catchupLsn: Option[LogSequenceNumber]) =
    runSync({
      SlotCatchup.insertOrUpdate(
        SlotCatchupRow(slotName, catchupLsn.map(_.asString())))
    })

  def getCurrentXlog: LogSequenceNumber = {
    implicit val rconv = GetResult(r => LogSequenceNumber.valueOf(r.nextString))
    runSync(
      sql"select pg_current_xlog_insert_location() as wal_current"
        .as[LogSequenceNumber]).head
  }
}

case class PgReplicationSlotStatus(slotName: String,
                                   isActive: Boolean,
                                   walDist: Option[Long],
                                   catchupDist: Option[Long],
                                   catchupLsn: Option[LogSequenceNumber]) extends SlotStatus {
  override def isBehind: Boolean = false
}

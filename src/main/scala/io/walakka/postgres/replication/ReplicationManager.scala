package io.walakka.postgres.replication

import java.sql.{DriverManager, Timestamp}
import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import io.walakka.postgres.models.Tables
import io.walakka.postgres.models.Tables.{PgReplicationSlots, SlotCatchup, SlotCatchupRow}
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}
import org.postgresql.{PGConnection, PGProperty}
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration


trait Db {
  lazy val db = Database.forConfig("db")
  lazy val config = ConfigFactory.load()

  protected def getConnection = {

    val url = config.getString("db.jdbcUrl")
    val props = new Properties()
    PGProperty.USER.set(props, "postgres")
    PGProperty.PASSWORD.set(props, "postgres")
    PGProperty.ASSUME_MIN_SERVER_VERSION.set(props, "9.4")
    PGProperty.REPLICATION.set(props, "database")
    PGProperty.PREFER_QUERY_MODE.set(props, "simple")

    DriverManager.getConnection(url, props).unwrap(classOf[PGConnection])
  }

  def getTxTsFromTxId(txId: String): Timestamp = {
    Await.result(db.run(sql"select pg_xact_commit_timestamp($txId)".as[Timestamp].head), Duration.Inf)
  }
}

trait ReplicationManager extends Db {

  val statusIntervalMs: Int

  def createReplicationSlot(slotName: String, outputPlugin: String = "test_decoding"): ActiveSlot = {
    getConnection.getReplicationAPI
      .createReplicationSlot()
      .logical()
      .withSlotName(slotName)
      .withOutputPlugin(outputPlugin)
      .make()

    ActiveSlot(slotName)
  }

  def dropReplicationSlot(slotName: String): Unit = getConnection.getReplicationAPI.dropReplicationSlot(slotName)

  def createReplicationStream(slotName: String, slotOptions: Map[String, String], statusInterval: Int = statusIntervalMs): PGReplicationStream = {
    val builder = getConnection.getReplicationAPI
      .replicationStream()
      .logical()
      .withSlotName(slotName)
      .withStatusInterval(statusInterval, TimeUnit.MILLISECONDS)
    //TODO: casting all options to strings, need to test
    slotOptions.foldLeft(builder){case (builder, t) => builder.withSlotOption(t._1, t._2)}
      .start()
  }


  def getReplicationSlotDist(slotName: String): DBIO[Int] = {
    sql"select pg_xlog_location_diff(pg_current_xlog_insert_location(), restart_lsn) from pg_replication_slots where slot_name = $slotName".as[Int].head
  }

  def getReplicationSlots: DBIO[Seq[ActiveSlot]] = {
    implicit val getActiveSlot = GetResult(r => ActiveSlot(r.nextString(), r.nextStringOption().map(LogSequenceNumber.valueOf)))
    sql"select prs.slot_name, sc.catchup_lsn from pg_replication_slots prs left join walakka.slot_catchup sc on prs.slot_name = sc.slot_name where prs.slot_name like 'walakka%'"
      .as[ActiveSlot]
  }

  def getReplicationSlotsSync = Await.result(db.run(getReplicationSlots), Duration.Inf)

  def getReplicationSlotStatus: DBIO[Seq[SlotStatus]] = {
    implicit val getSlotStatus = GetResult(r => SlotStatus(r.nextString(), LogSequenceNumber.valueOf(r.nextString()), r.nextLong, r.nextLongOption(), r.nextStringOption().map(LogSequenceNumber.valueOf)))
    sql"select prs.slot_name, pg_current_xlog_insert_location() as wal_current, pg_xlog_location_diff(pg_current_xlog_insert_location(), prs.confirmed_flush_lsn) as wal_dist, pg_xlog_location_diff(sc.catchup_lsn :: pg_lsn, prs.confirmed_flush_lsn) as dist, sc.catchup_lsn from pg_replication_slots prs left join walakka.slot_catchup sc on prs.slot_name = sc.slot_name"
      .as[SlotStatus]
  }

  def getReplicationSlotStatusSync: Seq[SlotStatus] = Await.result(db.run(getReplicationSlotStatus), Duration.Inf)

  def getReplicationSlotByName(slotName: String) = TableQuery[PgReplicationSlots].filter(_.slotName === slotName)

  def removeCatchupLsn(slotName: String) = Await.result(db.run(Tables.SlotCatchup.filter(_.slotName === slotName).delete), Duration.Inf)

  def insertCatchupLsn(slotName: String, catchupLsn: LogSequenceNumber) = Await.result(db.run{
    DBIO.seq(TableQuery[SlotCatchup] += SlotCatchupRow(Some(slotName), Some(catchupLsn.asString())))
  }, Duration.Inf)
}

case class ActiveSlot(slotName: String, catchupLsn: Option[LogSequenceNumber] = None)

case class SlotStatus(slotName: String, walCurrent: LogSequenceNumber, walDist: Long, catchupDist: Option[Long], catchupLsn: Option[LogSequenceNumber])

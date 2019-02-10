package io.walakka.postgres.replication

import java.sql.{DriverManager, Timestamp}
import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import io.walakka.postgres.models.Tables
import io.walakka.postgres.models.Tables.{SlotCatchup, SlotCatchupRow}
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}
import org.postgresql.{PGConnection, PGProperty}
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

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
    Await.result(
      db.run(sql"select pg_xact_commit_timestamp($txId)".as[Timestamp].head),
      Duration.Inf)
  }

  def runSync[T](expr: DBIO[T], duration: Duration = Duration.Inf): T =
    Await.result(db.run(expr), duration)
}

trait ReplicationManager extends Db {

  val statusIntervalMs: Int

  def generateSlotName(inputName: Option[String] = None): String = inputName.map(_.replaceAll("^\\w", "_")).getOrElse(Random.alphanumeric.take(8).toList.mkString).toLowerCase

  def createReplicationSlot(
      slotName: String = generateSlotName(),
      outputPlugin: String = "test_decoding"): (ActiveSlot, LogSequenceNumber) = {
    val (_, lsn) = runSync(sql"select * from pg_create_logical_replication_slot($slotName, $outputPlugin)"
        .as[(String, String)]
        .head)

    ActiveSlot(slotName) -> LogSequenceNumber.valueOf(lsn)
  }

  def dropReplicationSlot(slotName: String): Int =
    runSync(
      sql"select 1 from pg_drop_replication_slot($slotName)".as[Int].head)


  def createReplicationStream(
      slotName: String,
      slotOptions: Map[String, String],
      statusInterval: Int = statusIntervalMs): PGReplicationStream = {
    //use an anonymous connection instance to pass with the replication stream
    val builder = getConnection.getReplicationAPI
      .replicationStream()
      .logical()
      .withSlotName(slotName)
      .withStatusInterval(statusInterval, TimeUnit.MILLISECONDS)
    //TODO: casting all options to strings, need to test
    slotOptions
      .foldLeft(builder) {
        case (builder, t) => builder.withSlotOption(t._1, t._2)
      }
      .start()
  }

  def getReplicationSlotDist(slotName: String): DBIO[Int] = {
    sql"select pg_xlog_location_diff(pg_current_xlog_insert_location(), restart_lsn) from pg_replication_slots where slot_name = $slotName"
      .as[Int]
      .head
  }

  def getReplicationSlots: DBIO[Seq[ActiveSlot]] = {
    implicit val getActiveSlot = GetResult(
      r =>
        ActiveSlot(r.nextString(),
                   r.nextStringOption().map(LogSequenceNumber.valueOf)))
    sql"select prs.slot_name, sc.catchup_lsn from walakka.slot_catchup sc left join pg_replication_slots prs on prs.slot_name = sc.slot_name where prs.slot_name like 'walakka%'"
      .as[ActiveSlot]
  }

  def getReplicationSlotsSync = runSync(getReplicationSlots)

  def getReplicationSlotStatus: DBIO[Seq[SlotStatus]] = {
    implicit val getSlotStatus = GetResult(
      r =>
        SlotStatus(r.nextString(),
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
      .as[SlotStatus]
  }

  def getReplicationSlotStatusSync: Seq[SlotStatus] =
    runSync(getReplicationSlotStatus)

  def removeCatchupLsn(slotName: String): Int =
    runSync(Tables.SlotCatchup.filter(_.slotName === slotName).delete)

  def insertCatchupLsn(slotName: String, catchupLsn: Option[LogSequenceNumber] = None) =
    runSync({
      DBIO.seq(
        SlotCatchup += SlotCatchupRow(Some(slotName),
          catchupLsn.map(_.asString())))
    })

  def updateCatchupLsn(slotName: String, catchupLsn: Option[LogSequenceNumber]) = runSync({
    SlotCatchup.insertOrUpdate(SlotCatchupRow(Some(slotName), catchupLsn.map(_.asString())))
  })

  def getCurrentXlog: LogSequenceNumber = {
    implicit val rconv = GetResult(r => LogSequenceNumber.valueOf(r.nextString))
    runSync(sql"select pg_current_xlog_insert_location() as wal_current".as[LogSequenceNumber]).head
  }
}

case class ActiveSlot(slotName: String,
                      catchupLsn: Option[LogSequenceNumber] = None)

case class SlotStatus(slotName: String,
                      isActive: Boolean,
                      walDist: Option[Long],
                      catchupDist: Option[Long],
                      catchupLsn: Option[LogSequenceNumber])

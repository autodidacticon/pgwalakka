package io.walakka.postgres.models

// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = slick.jdbc.PostgresProfile
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.jdbc.JdbcProfile

  import profile.api._
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema
    : profile.SchemaDescription = PgReplicationSlots.schema ++ SlotCatchup.schema

  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Pgr
    *
    * @param slotName          Database column slot_name SqlType(name), Default(None)
    * @param plugin            Database column plugin SqlType(name), Default(None)
    * @param active            Database column active SqlType(bool), Default(None)
    * @param activePid         Database column active_pid SqlType(int4), Default(None)
    * @param restartLsn        Database column restart_lsn SqlType(pg_lsn), Length(2147483647,false), Default(None)
    * @param confirmedFlushLsn Database column confirmed_flush_lsn SqlType(pg_lsn), Length(2147483647,false), Default(None) */
  case class PgReplicationSlotsRow(slotName: Option[String] = None,
                                   plugin: Option[String] = None,
                                   active: Option[Boolean] = None,
                                   activePid: Option[Int] = None,
                                   restartLsn: Option[String] = None,
                                   confirmedFlushLsn: Option[String] = None)

  /** GetResult implicit for fetching PgrRow objects using plain SQL queries */
  implicit def GetResultPgReplicationSlotsRow(
      implicit e0: GR[Option[String]],
      e1: GR[Option[Boolean]],
      e2: GR[Option[Int]]): GR[PgReplicationSlotsRow] = GR { prs =>
    import prs._
    PgReplicationSlotsRow.tupled(
      (<<?[String],
       <<?[String],
       <<?[Boolean],
       <<?[Int],
       <<?[String],
       <<?[String]))
  }

  /** Table description of table pg_replication_slots. Objects of this class serve as prototypes for rows in queries. */
  class PgReplicationSlots(_tableTag: Tag)
      extends profile.api.Table[PgReplicationSlotsRow](_tableTag,
                                                       "pg_replication_slots") {
    def * =
      (slotName, plugin, active, activePid, restartLsn, confirmedFlushLsn) <> (PgReplicationSlotsRow.tupled, PgReplicationSlotsRow.unapply)

    /** Database column slot_name SqlType(name), Default(None) */
    val slotName: Rep[Option[String]] =
      column[Option[String]]("slot_name", O.Default(None))

    /** Database column plugin SqlType(name), Default(None) */
    val plugin: Rep[Option[String]] =
      column[Option[String]]("plugin", O.Default(None))

    /** Database column active SqlType(bool), Default(None) */
    val active: Rep[Option[Boolean]] =
      column[Option[Boolean]]("active", O.Default(None))

    /** Database column active_pid SqlType(int4), Default(None) */
    val activePid: Rep[Option[Int]] =
      column[Option[Int]]("active_pid", O.Default(None))

    /** Database column restart_lsn SqlType(pg_lsn), Length(2147483647,false), Default(None) */
    val restartLsn: Rep[Option[String]] = column[Option[String]](
      "restart_lsn",
      O.Length(2147483647, varying = false),
      O.Default(None))

    /** Database column confirmed_flush_lsn SqlType(pg_lsn), Length(2147483647,false), Default(None) */
    val confirmedFlushLsn: Rep[Option[String]] = column[Option[String]](
      "confirmed_flush_lsn",
      O.Length(2147483647, varying = false),
      O.Default(None))
  }

  /** Collection-like TableQuery object for table Pgr */
  lazy val PgReplicationSlots = new TableQuery(
    tag => new PgReplicationSlots(tag))

  /** Entity class storing rows of table SlotCatchup
    *  @param slotName Database column slot_name SqlType(text), Default(None)
    *  @param catchupLsn Database column catchup_lsn SqlType(pg_lsn), Length(2147483647,false), Default(None) */
  case class SlotCatchupRow(slotName: Option[String] = None,
                            catchupLsn: Option[String] = None)

  /** GetResult implicit for fetching SlotCatchupRow objects using plain SQL queries */
  implicit def GetResultSlotCatchupRow(
      implicit e0: GR[Option[String]]): GR[SlotCatchupRow] = GR { prs =>
    import prs._
    SlotCatchupRow.tupled((<<?[String], <<?[String]))
  }

  /** Table description of table slot_catchup. Objects of this class serve as prototypes for rows in queries. */
  class SlotCatchup(_tableTag: Tag)
      extends profile.api.Table[SlotCatchupRow](_tableTag,
                                                Some("walakka"),
                                                "slot_catchup") {
    def * =
      (slotName, catchupLsn) <> (SlotCatchupRow.tupled, SlotCatchupRow.unapply)

    /** Database column slot_name SqlType(text), Default(None) */
    val slotName: Rep[Option[String]] =
      column[Option[String]]("slot_name", O.Default(None))

    /** Database column catchup_lsn SqlType(pg_lsn), Length(2147483647,false), Default(None) */
    val catchupLsn: Rep[Option[String]] = column[Option[String]](
      "catchup_lsn",
      O.Length(2147483647, varying = false),
      O.Default(None))
  }

  /** Collection-like TableQuery object for table SlotCatchup */
  lazy val SlotCatchup = new TableQuery(tag => new SlotCatchup(tag))

}

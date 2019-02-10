package io.pgwalakka.postgres.models
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = slick.jdbc.PostgresProfile
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = FlywaySchemaHistory.schema ++ SlotCatchup.schema ++ Test.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table FlywaySchemaHistory
   *  @param installedRank Database column installed_rank SqlType(int4), PrimaryKey
   *  @param version Database column version SqlType(varchar), Length(50,true), Default(None)
   *  @param description Database column description SqlType(varchar), Length(200,true)
   *  @param `type` Database column type SqlType(varchar), Length(20,true)
   *  @param script Database column script SqlType(varchar), Length(1000,true)
   *  @param checksum Database column checksum SqlType(int4), Default(None)
   *  @param installedBy Database column installed_by SqlType(varchar), Length(100,true)
   *  @param installedOn Database column installed_on SqlType(timestamp)
   *  @param executionTime Database column execution_time SqlType(int4)
   *  @param success Database column success SqlType(bool) */
  case class FlywaySchemaHistoryRow(installedRank: Int, version: Option[String] = None, description: String, `type`: String, script: String, checksum: Option[Int] = None, installedBy: String, installedOn: java.sql.Timestamp, executionTime: Int, success: Boolean)
  /** GetResult implicit for fetching FlywaySchemaHistoryRow objects using plain SQL queries */
  implicit def GetResultFlywaySchemaHistoryRow(implicit e0: GR[Int], e1: GR[Option[String]], e2: GR[String], e3: GR[Option[Int]], e4: GR[java.sql.Timestamp], e5: GR[Boolean]): GR[FlywaySchemaHistoryRow] = GR{
    prs => import prs._
    FlywaySchemaHistoryRow.tupled((<<[Int], <<?[String], <<[String], <<[String], <<[String], <<?[Int], <<[String], <<[java.sql.Timestamp], <<[Int], <<[Boolean]))
  }
  /** Table description of table flyway_schema_history. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: type */
  class FlywaySchemaHistory(_tableTag: Tag) extends profile.api.Table[FlywaySchemaHistoryRow](_tableTag, "flyway_schema_history") {
    def * = (installedRank, version, description, `type`, script, checksum, installedBy, installedOn, executionTime, success) <> (FlywaySchemaHistoryRow.tupled, FlywaySchemaHistoryRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(installedRank), version, Rep.Some(description), Rep.Some(`type`), Rep.Some(script), checksum, Rep.Some(installedBy), Rep.Some(installedOn), Rep.Some(executionTime), Rep.Some(success)).shaped.<>({r=>import r._; _1.map(_=> FlywaySchemaHistoryRow.tupled((_1.get, _2, _3.get, _4.get, _5.get, _6, _7.get, _8.get, _9.get, _10.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column installed_rank SqlType(int4), PrimaryKey */
    val installedRank: Rep[Int] = column[Int]("installed_rank", O.PrimaryKey)
    /** Database column version SqlType(varchar), Length(50,true), Default(None) */
    val version: Rep[Option[String]] = column[Option[String]]("version", O.Length(50,varying=true), O.Default(None))
    /** Database column description SqlType(varchar), Length(200,true) */
    val description: Rep[String] = column[String]("description", O.Length(200,varying=true))
    /** Database column type SqlType(varchar), Length(20,true)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `type`: Rep[String] = column[String]("type", O.Length(20,varying=true))
    /** Database column script SqlType(varchar), Length(1000,true) */
    val script: Rep[String] = column[String]("script", O.Length(1000,varying=true))
    /** Database column checksum SqlType(int4), Default(None) */
    val checksum: Rep[Option[Int]] = column[Option[Int]]("checksum", O.Default(None))
    /** Database column installed_by SqlType(varchar), Length(100,true) */
    val installedBy: Rep[String] = column[String]("installed_by", O.Length(100,varying=true))
    /** Database column installed_on SqlType(timestamp) */
    val installedOn: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("installed_on")
    /** Database column execution_time SqlType(int4) */
    val executionTime: Rep[Int] = column[Int]("execution_time")
    /** Database column success SqlType(bool) */
    val success: Rep[Boolean] = column[Boolean]("success")

    /** Index over (success) (database name flyway_schema_history_s_idx) */
    val index1 = index("flyway_schema_history_s_idx", success)
  }
  /** Collection-like TableQuery object for table FlywaySchemaHistory */
  lazy val FlywaySchemaHistory = new TableQuery(tag => new FlywaySchemaHistory(tag))

  /** Entity class storing rows of table SlotCatchup
   *  @param slotName Database column slot_name SqlType(text), PrimaryKey
   *  @param catchupLsn Database column catchup_lsn SqlType(text), Default(None) */
  case class SlotCatchupRow(slotName: String, catchupLsn: Option[String] = None)
  /** GetResult implicit for fetching SlotCatchupRow objects using plain SQL queries */
  implicit def GetResultSlotCatchupRow(implicit e0: GR[String], e1: GR[Option[String]]): GR[SlotCatchupRow] = GR{
    prs => import prs._
    SlotCatchupRow.tupled((<<[String], <<?[String]))
  }
  /** Table description of table slot_catchup. Objects of this class serve as prototypes for rows in queries. */
  class SlotCatchup(_tableTag: Tag) extends profile.api.Table[SlotCatchupRow](_tableTag, Some("walakka"), "slot_catchup") {
    def * = (slotName, catchupLsn) <> (SlotCatchupRow.tupled, SlotCatchupRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(slotName), catchupLsn).shaped.<>({r=>import r._; _1.map(_=> SlotCatchupRow.tupled((_1.get, _2)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column slot_name SqlType(text), PrimaryKey */
    val slotName: Rep[String] = column[String]("slot_name", O.PrimaryKey)
    /** Database column catchup_lsn SqlType(text), Default(None) */
    val catchupLsn: Rep[Option[String]] = column[Option[String]]("catchup_lsn", O.Default(None))
  }
  /** Collection-like TableQuery object for table SlotCatchup */
  lazy val SlotCatchup = new TableQuery(tag => new SlotCatchup(tag))

  /** Entity class storing rows of table Test
   *  @param a Database column a SqlType(bigserial), AutoInc, PrimaryKey
   *  @param b Database column b SqlType(int4), Default(None) */
  case class TestRow(a: Long, b: Option[Int] = None)
  /** GetResult implicit for fetching TestRow objects using plain SQL queries */
  implicit def GetResultTestRow(implicit e0: GR[Long], e1: GR[Option[Int]]): GR[TestRow] = GR{
    prs => import prs._
    TestRow.tupled((<<[Long], <<?[Int]))
  }
  /** Table description of table test. Objects of this class serve as prototypes for rows in queries. */
  class Test(_tableTag: Tag) extends profile.api.Table[TestRow](_tableTag, "test") {
    def * = (a, b) <> (TestRow.tupled, TestRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(a), b).shaped.<>({r=>import r._; _1.map(_=> TestRow.tupled((_1.get, _2)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column a SqlType(bigserial), AutoInc, PrimaryKey */
    val a: Rep[Long] = column[Long]("a", O.AutoInc, O.PrimaryKey)
    /** Database column b SqlType(int4), Default(None) */
    val b: Rep[Option[Int]] = column[Option[Int]]("b", O.Default(None))
  }
  /** Collection-like TableQuery object for table Test */
  lazy val Test = new TableQuery(tag => new Test(tag))
}

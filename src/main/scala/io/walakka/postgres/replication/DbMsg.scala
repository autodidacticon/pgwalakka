package io.walakka.postgres.replication

import org.json4s.JsonDSL._
import org.json4s._

case class DbMsg(schemaName: String,
                 tableName: String,
                 transactionType: String,
                 data: JValue,
                 meta: JValue)

object DbMsgBuilder {
  final val Delete = "delete"

  final val Update = "update"

  final val Insert = "insert"
}

class DbMsgBuilder {
  var schemaName: Option[String] = None
  var tableName: Option[String] = None
  var transactionType: Option[String] = None
  var data: JValue = JNull
  var meta: JValue = JNull

  def mergeNumData(tup: (String, Option[Double])) =
    data = (data merge pair2jvalue(tup))

  def mergeStringData(tup: (String, Option[String])) =
    data = (data merge pair2jvalue(tup))

  def mergeMeta(tup: (String, Option[String])) =
    meta = (meta merge pair2jvalue(tup))

  def build: Option[DbMsg] =
    schemaName.map(_ =>
      DbMsg(schemaName.get, tableName.get, transactionType.get, data, meta))
}

case class Datum(columnName: String,
                 dataType: String,
                 numData: Option[Double],
                 stringData: Option[String])

class DatumBuilder {
  var columnName: Option[String] = None
  var dataType: Option[String] = None
  var numData: Option[Double] = None
  var stringData: Option[String] = None

  def getNumDataTuple = columnName.map(_ -> numData)

  def getStringDataTuple = columnName.map(_ -> stringData)

  def getMetaTuple = columnName.map(_ -> dataType)

  def build: Datum =
    Datum(columnName.getOrElse(""), dataType.getOrElse(""), numData, stringData)
}

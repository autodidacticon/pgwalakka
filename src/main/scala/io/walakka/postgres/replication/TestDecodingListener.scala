package io.walakka.postgres.replication

import io.walakka.postgres.replication.logicaldecoding.{PgLogicalDecodingListener, PgLogicalDecodingParser}
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.{ErrorNode, TerminalNode}

import scala.util.Try

class TestDecodingListener() extends PgLogicalDecodingListener {

  private var msgBuilder: DbMsgBuilder = new DbMsgBuilder()

  def getMsg: Option[DbMsg] = msgBuilder.build

  private var datumBuilder: DatumBuilder = new DatumBuilder()

  def getDatum: Datum = datumBuilder.build

  private def resetDatum = datumBuilder = new DatumBuilder()
  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#logline}.
    *
    * @param ctx the parse tree
    */
  override def enterLogline(ctx: PgLogicalDecodingParser.LoglineContext): Unit = {
    msgBuilder = new DbMsgBuilder()
  }

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#txStatement}.
    *
    * @param ctx the parse tree
    */
  override def enterTxStatement(ctx: PgLogicalDecodingParser.TxStatementContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#dmlStatement}.
    *
    * @param ctx the parse tree
    */
  override def enterDmlStatement(ctx: PgLogicalDecodingParser.DmlStatementContext): Unit = {
    msgBuilder.schemaName = Some(ctx.table().schemaname().Identifier().getSymbol.getText)

  }

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#dmlStatement}.
    *
    * @param ctx the parse tree
    */
  override def exitDmlStatement(ctx: PgLogicalDecodingParser.DmlStatementContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#insertOp}.
    *
    * @param ctx the parse tree
    */
  override def enterInsertOp(ctx: PgLogicalDecodingParser.InsertOpContext): Unit = msgBuilder.transactionType = Some(DbMsgBuilder.Insert)

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#insertOp}.
    *
    * @param ctx the parse tree
    */
  override def exitInsertOp(ctx: PgLogicalDecodingParser.InsertOpContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#updateOp}.
    *
    * @param ctx the parse tree
    */
  override def enterUpdateOp(ctx: PgLogicalDecodingParser.UpdateOpContext): Unit = msgBuilder.transactionType = Some(DbMsgBuilder.Update)

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#updateOp}.
    *
    * @param ctx the parse tree
    */
  override def exitUpdateOp(ctx: PgLogicalDecodingParser.UpdateOpContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#deleteOp}.
    *
    * @param ctx the parse tree
    */
  override def enterDeleteOp(ctx: PgLogicalDecodingParser.DeleteOpContext): Unit = msgBuilder.transactionType = Some(DbMsgBuilder.Delete)

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#deleteOp}.
    *
    * @param ctx the parse tree
    */
  override def exitDeleteOp(ctx: PgLogicalDecodingParser.DeleteOpContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#oldKeyValuePair}.
    *
    * @param ctx the parse tree
    */
  override def enterKeyValuePair(ctx: PgLogicalDecodingParser.KeyValuePairContext): Unit = resetDatum

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#oldKeyValuePair}.
    *
    * @param ctx the parse tree
    */
  override def exitKeyValuePair(ctx: PgLogicalDecodingParser.KeyValuePairContext): Unit = datumBuilder.getMetaTuple.map(msgBuilder.mergeMeta)

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#value}.
    *
    * @param ctx the parse tree
    */
  override def enterValue(ctx: PgLogicalDecodingParser.ValueContext): Unit = datumBuilder.numData = Try(ctx.getText.toDouble).toOption

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#value}.
    *
    * @param ctx the parse tree
    */
  override def exitValue(ctx: PgLogicalDecodingParser.ValueContext): Unit = datumBuilder.getNumDataTuple.map(msgBuilder.mergeNumData)

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#table}.
    *
    * @param ctx the parse tree
    */
  override def enterTable(ctx: PgLogicalDecodingParser.TableContext): Unit = {}

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#table}.
    *
    * @param ctx the parse tree
    */
  override def exitTable(ctx: PgLogicalDecodingParser.TableContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#schemaname}.
    *
    * @param ctx the parse tree
    */
  override def enterSchemaname(ctx: PgLogicalDecodingParser.SchemanameContext): Unit = msgBuilder.schemaName = Some(ctx.Identifier().getSymbol.getText)

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#schemaname}.
    *
    * @param ctx the parse tree
    */
  override def exitSchemaname(ctx: PgLogicalDecodingParser.SchemanameContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#tablename}.
    *
    * @param ctx the parse tree
    */
  override def enterTablename(ctx: PgLogicalDecodingParser.TablenameContext): Unit = msgBuilder.tableName = Some(ctx.Identifier().getSymbol.getText)

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#tablename}.
    *
    * @param ctx the parse tree
    */
  override def exitTablename(ctx: PgLogicalDecodingParser.TablenameContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#columnname}.
    *
    * @param ctx the parse tree
    */
  override def enterColumnname(ctx: PgLogicalDecodingParser.ColumnnameContext): Unit = datumBuilder.columnName = Some(ctx.Identifier().getSymbol.getText)

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#columnname}.
    *
    * @param ctx the parse tree
    */
  override def exitColumnname(ctx: PgLogicalDecodingParser.ColumnnameContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#typedef}.
    *
    * @param ctx the parse tree
    */
  override def enterTypedef(ctx: PgLogicalDecodingParser.TypedefContext): Unit = datumBuilder.dataType = Some(ctx.Identifier().getSymbol.getText)

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#typedef}.
    *
    * @param ctx the parse tree
    */
  override def exitTypedef(ctx: PgLogicalDecodingParser.TypedefContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#quotedValue}.
    *
    * @param ctx the parse tree
    */
  override def enterQuotedValue(ctx: PgLogicalDecodingParser.QuotedValueContext): Unit = datumBuilder.stringData = Some(ctx.QuotedString().getSymbol.getText.stripPrefix("'").stripSuffix("'"))

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#quotedValue}.
    *
    * @param ctx the parse tree
    */
  override def exitQuotedValue(ctx: PgLogicalDecodingParser.QuotedValueContext): Unit = datumBuilder.getStringDataTuple.map(msgBuilder.mergeStringData)

  override def visitTerminal(node: TerminalNode): Unit = {}

  override def visitErrorNode(node: ErrorNode): Unit = {}

  override def enterEveryRule(ctx: ParserRuleContext): Unit = {}

  override def exitEveryRule(ctx: ParserRuleContext): Unit = {}

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#logline}.
    *
    * @param ctx the parse tree
    */
  override def exitLogline(ctx: PgLogicalDecodingParser.LoglineContext): Unit = {}

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#txStatement}.
    *
    * @param ctx the parse tree
    */
  override def exitTxStatement(ctx: PgLogicalDecodingParser.TxStatementContext): Unit = {}

  /**
    * Enter a parse tree produced by {@link PgLogicalDecodingParser#commitTimestamp}.
    *
    * @param ctx the parse tree
    */
  override def enterCommitTimestamp(ctx: PgLogicalDecodingParser.CommitTimestampContext): Unit = {}

  /**
    * Exit a parse tree produced by {@link PgLogicalDecodingParser#commitTimestamp}.
    *
    * @param ctx the parse tree
    */
  override def exitCommitTimestamp(ctx: PgLogicalDecodingParser.CommitTimestampContext): Unit = {}
}

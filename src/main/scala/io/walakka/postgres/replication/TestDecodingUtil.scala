package io.walakka.postgres.replication

import io.walakka.postgres.replication.logicaldecoding.{PgLogicalDecodingLexer, PgLogicalDecodingParser}
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.antlr.v4.runtime.{CharStreams, CommonTokenStream}

trait TestDecodingUtil {

  def parseMsg(msg: String): Option[DbMsg] = {
    val lexer = new PgLogicalDecodingLexer(CharStreams.fromString(msg))
    val tokens = new CommonTokenStream(lexer)
    val parser = new PgLogicalDecodingParser(tokens)

    val listener = new TestDecodingListener()
    val tree = parser.logline()
    ParseTreeWalker.DEFAULT.walk(listener, tree)
    listener.getMsg
  }
}

package io.walakka.postgres.replication

import java.sql.Timestamp

import org.json4s.JsonAST.{JDouble, JNothing, JString}

class TestDecodingListenerSpec extends WordSpecLike with Matchers with MockitoSugar {

  val insertLogLine: String = "table public.data: INSERT: id[int4]:2 data[text]:'arg' nullable[text]:null"
  val updateLogLine: String = "table public.data: UPDATE: id[int4]:2 data[text]:'arg'"
  val deleteLogLine: String = "table public.data: DELETE: id[int4]:2"

  trait listenerTest extends TestDecodingUtil {
    val db = mock[ReplicationManager]
    when(db.getTxTsFromTxId(anyString())).thenReturn(new Timestamp(System.currentTimeMillis()))
    def passed: Assertion
  }

  "TestDecodingListener" should {
    "extract data from insert statements" in {
      new listenerTest {
        def passed = {
          val msg = parseMsg(insertLogLine).get

          assert(msg.data \ "id" == JDouble(2) && msg.data \ "data" == JString("arg") && msg.data \ "nullable" == JNothing && msg.transactionType == DbMsgBuilder.Insert)
        }
      }.passed
    }

    "extract data from update statements" in {
      new listenerTest {
        def passed = {
          val msg = parseMsg(updateLogLine).get

          assert(msg.data \ "id" == JDouble(2) && msg.data \ "data" == JString("arg") && msg.transactionType == DbMsgBuilder.Update)
        }
      }.passed
    }

    "extract primary key data from delete statements" in {
      new listenerTest {
        def passed = {
          val msg = parseMsg(deleteLogLine).get

          assert(msg.data \ "id" == JDouble(2) && msg.transactionType == DbMsgBuilder.Delete)
        }
      }.passed
    }
  }

}

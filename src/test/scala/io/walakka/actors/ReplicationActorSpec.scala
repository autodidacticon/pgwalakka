package io.walakka.actors

import akka.actor.ActorSystem
import akka.testkit.TestKit
import io.walakka.postgres.replication.Db
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.mockito.Mockito._
import org.postgresql.PGConnection

import scala.language.postfixOps


class ReplicationActorSpec(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll with MockitoSugar {

  def this() = this(ActorSystem("ReplicationActorSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "A Replication Actor" should {
    "call getLastFlushedLsn when queried for status" in {
      val mockStream: PGConnection => PGReplicationStream = _ => mock[PGReplicationStream]
      val mockCnxn = mock[PGConnection]
      val mockDb = mock[Db]
      val testActor = system.actorOf(ReplicationActor.props("test", mockCnxn, mockStream))
//      testActor ! StatusQuery
//      when(mockStream.getLastFlushedLSN).thenReturn(LogSequenceNumber.valueOf(1L))
    }
  }
}

package io.walakka.actors

import java.nio.ByteBuffer

import akka.actor.{Actor, ActorSystem, PoisonPill, Terminated}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import io.walakka.producers.DummyProducer
import org.mockito.Mockito.{spy, times, verify, when}
import org.postgresql.PGConnection
import org.postgresql.replication.{LogSequenceNumber, PGReplicationStream}
import org.scalatest.concurrent.Futures
import org.scalatest.mockito.MockitoSugar
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.Future

class PgReplicationActorSpec extends TestKit(ActorSystem("PgReplicationActorSpec"))
  with ImplicitSender
  with Matchers
  with AsyncWordSpecLike
  with BeforeAndAfterAll with BeforeAndAfterEach with MockitoSugar with Futures {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  trait TestPgReplicationActorSpec {
    val mockConnection = mock[PGConnection]
    val mockStream = mock[PGReplicationStream]
    val producer = new DummyProducer{}

    def passed: Future[Assertion]
  }

  "PgReplicationActor" should {
    "start consuming messages when it receives StartProcessing" in {
      new TestPgReplicationActorSpec {
        val (pgReplicationActorRef, pgReplicationActorSpy) = {
          val t = TestActorRef(new PgReplicationActor(mockConnection, mockStream, producer))
          val pgReplicationActorSpy = spy(t.underlyingActor)
          TestActorRef(pgReplicationActorSpy) -> pgReplicationActorSpy
        }
        when(mockStream.isClosed).thenReturn(false)
        when(mockStream.read).thenReturn(ByteBuffer.wrap("test".getBytes))
        when(mockStream.getLastReceiveLSN).thenReturn(LogSequenceNumber.valueOf(1L))
        override def passed: Future[Assertion] = {
          pgReplicationActorRef ! PgReplicationActor.StartProcessing
          Thread.sleep(100)
          verify(pgReplicationActorSpy, times(1)).processWal
          pgReplicationActorSpy.stream.isClosed shouldBe false
        }
      }.passed
    }

    "stop consuming messages when it receives StopProcessing" in {
      new TestPgReplicationActorSpec {
        val pgReplicationActorRef = TestActorRef(new PgReplicationActor(mockConnection, mockStream, producer))
        override def passed: Future[Assertion] = {
          val probe = TestProbe()
          probe.watch(pgReplicationActorRef)
          pgReplicationActorRef ! PgReplicationActor.StartProcessing
          pgReplicationActorRef ! PgReplicationActor.StopProcessing
          probe.expectTerminated(pgReplicationActorRef) shouldBe Terminated(pgReplicationActorRef)(true, true)
        }
      }.passed
    }
  }
}
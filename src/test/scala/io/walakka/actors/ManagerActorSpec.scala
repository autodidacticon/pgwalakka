package io.walakka.actors

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{TestKit, TestProbe}
import io.walakka.postgres.replication.ActiveSlot
import org.postgresql.PGConnection
import org.postgresql.replication.PGReplicationStream
import org.scalatest._
import org.scalatest.concurrent.Futures
import org.scalatest.mockito.MockitoSugar

import scala.language.postfixOps


class ManagerActorSpec(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with AsyncWordSpecLike
    with BeforeAndAfterAll with BeforeAndAfterEach with MockitoSugar with Futures with WalAkkaSystem {

  val testMgrName = "testManager"

  implicit val ec = system.dispatcher

  def this() = this(ActorSystem("ManagerActorSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  override def beforeEach: Unit = system.actorSelection(s"user/$testMgrName")
    .resolveOne()
    .foreach(ref => ref ! PoisonPill)

  val actorSystem = system

  val testReplOpts = ReplicationOptions(1, Map.empty, 1, 1)

  class TestMgr(replicationOptions: ReplicationOptions = testReplOpts, refOpt: Option[ActorRef] = None)
    extends ManagerActor(replicationOptions, refOpt) {
    override def createReplicationStream(slotName: String,
                                         slotOptions: Map[String, String],
                                         statusInterval: Int)(cnxn: PGConnection): PGReplicationStream = mock[PGReplicationStream]
  }

  def getTestMgr(name: String = testMgrName, testProbeRef: Option[ActorRef] = None) =
    system.actorOf(Props(new TestMgr(testReplOpts, testProbeRef)), name)

  "WalAkka" should {
    "create manager actors" in {
      val mgrRef = getTestMgr()
      system.actorSelection(s"user/$testMgrName")
        .resolveOne().map(actorRef => {
        actorRef shouldEqual mgrRef
      })
    }
  }

  "A Manager Actor" should {
    "create replication actors with the slot name as the actor path" in {
      val testMgr = getTestMgr()
      val testSlotName = "test"
      testMgr ! Seq(ActiveSlot(testSlotName))
      Thread.sleep(500)
      system.actorSelection(s"/user/$testMgrName/$testSlotName")
        .resolveOne().map(ref => {
        ref.path.name shouldEqual testSlotName
      })
    }

    "terminate replication actors by slot name" in {
      val testProbe = TestProbe()
      val testMgr = getTestMgr(testProbeRef = Some(testProbe.ref))
      val testSlotName = "test"
      testMgr ! Seq(ActiveSlot(testSlotName))
      Thread.sleep(1000)
      system.actorSelection(s"/user/$testMgrName/$testSlotName")
          .resolveOne()
          .map(ref => {
            testMgr ! ManagerActor.TerminateActor(testSlotName)
            testProbe.expectMsg(ActorTerminated(ref)) match {
              case ActorTerminated(actorRef) => actorRef shouldEqual ref
            }
          })
    }
  }
}

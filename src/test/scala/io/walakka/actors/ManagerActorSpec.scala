//package io.walakka.actors
//
//import akka.actor.{ActorSystem, PoisonPill, Props}
//import akka.pattern.ask
//import akka.testkit.{TestKit, TestProbe}
//import io.walakka.postgres.replication.{ActiveSlot, ReplicationManager}
//import io.walakka.producers.DummyProducer
//import org.postgresql.PGConnection
//import org.scalatest._
//import org.scalatest.concurrent.Futures
//import org.scalatest.mockito.MockitoSugar
//
//import scala.concurrent.duration.Duration
//import scala.concurrent.{Await, Future}
//import scala.language.postfixOps
//
//
//class ManagerActorSpec(_system: ActorSystem)
//  extends TestKit(_system)
//    with Matchers
//    with AsyncWordSpecLike
//    with BeforeAndAfterAll with BeforeAndAfterEach with MockitoSugar with Futures with WalAkkaSystem {
//
//  val testMgrName = "testManager"
//
//  implicit val ec = system.dispatcher
//
//  def this() = this(ActorSystem("ManagerActorSpec"))
//
//  override def afterAll: Unit = {
//    TestKit.shutdownActorSystem(system)
//  }
//
//  override def beforeEach: Unit = system.actorSelection(s"user/$testMgrName")
//    .resolveOne()
//    .foreach(ref => ref ? PoisonPill)
//
//  override def afterEach: Unit = system.actorSelection(s"user/$testMgrName")
//    .resolveOne()
//    .foreach(ref => ref ? PoisonPill)
//
//  val actorSystem = system
//
//  val testReplOpts = ReplicationOptions(1, Map.empty, 1, 1)
//
//  class TestMgr(replicationOptions: ReplicationOptions = testReplOpts, cnxnGen: () => PGConnection, replMgr: ReplicationManager, producer: DummyProducer)
//    extends ManagerActor(replicationOptions, cnxnGen, replMgr, producer)
//
//  def getTestMgr(name: String = testMgrName, cnxnGen: () => PGConnection, replMgr: ReplicationManager, producer: DummyProducer) =
//    Await.result(system.actorSelection(s"user/$testMgrName").resolveOne()
//      .fallbackTo(
//        Future {
//          system.actorOf(Props(new TestMgr(testReplOpts, cnxnGen, replMgr, producer)), name)
//        }
//      ), Duration.Inf)
//
//  "WalAkka" should {
////    "create manager actors" in {
//////      val mockMgr = mock[ReplicationManager]
//////      mockMgr.
//////      val mgrRef = getTestMgr(cnxnGen = () => mock[PGConnection], producer = new DummyProducer {})
//////      system.actorSelection(s"user/$testMgrName")
//////        .resolveOne().map(actorRef => {
//////        actorRef shouldEqual mgrRef
//////      })
////    }
//  }
//
//  "A Manager Actor" should {
//    "create replication actors with the slot name as the actor path" in {
//      val testMgr = getTestMgr(cnxnGen = () => mock[PGConnection], producer = new DummyProducer {})
//      val testSlotName = "test"
//      testMgr ? ActiveSlot(testSlotName)
//      Thread.sleep(500)
//      system.actorSelection(s"/user/$testMgrName/$testSlotName")
//        .resolveOne().map(ref => {
//        ref.path.name shouldEqual testSlotName
//      })
//    }
//
//    "terminate replication actors by slot name" in {
//      val testProbe = TestProbe()
//      val testMgr = getTestMgr(cnxnGen = () => mock[PGConnection], producer = new DummyProducer {})
//      val testSlotName = "test"
//      testMgr ? ActiveSlot(testSlotName)
//      Thread.sleep(1000)
//      system.actorSelection(s"/user/$testMgrName/$testSlotName")
//        .resolveOne()
//        .map(ref => {
//          testMgr ! ManagerActor.TerminateActor(testSlotName)
//          testProbe.expectMsg(ActorTerminated(ref)) match {
//            case ActorTerminated(actorRef) => actorRef shouldEqual ref
//          }
//        })
//    }
//  }
//}

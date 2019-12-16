package io.walakka.actors

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActorRef, TestActors, TestKit}
import io.walakka.postgres.replication.ActiveSlot
import io.walakka.producers.DummyProducer
import org.scalatest._
import org.scalatest.concurrent.Futures
import org.scalatest.mockito.MockitoSugar

import scala.language.postfixOps


class WalAkkaSystemSpec
  extends TestKit(ActorSystem("WalAkkaSystemSpec"))
    with ImplicitSender
    with Matchers
    with AsyncWordSpecLike
    with BeforeAndAfterAll with BeforeAndAfterEach with MockitoSugar with Futures with WalAkkaSystem {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  override val actorSystem: ActorSystem = system

  "WalAkkaSystem" should {
    "create manager actors with a given name" in {
      val testManagerName = "testManager"
      val replOpts = ReplicationOptions(1L, Map.empty, 1, 1)
      val testManager = createManagerActor(replOpts, testManagerName, getConnection, new DummyProducer {})
      system.actorSelection(s"user/$testManagerName").resolveOne()
        .map(_ shouldEqual testManager)
    }
  }
}

package io.walakka.actors

import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import akka.actor.typed.ActorRef
import io.walakka.actors.Replication.ReplicationSlot
import org.mockito.Mockito.verify
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.mockito.MockitoSugar._

class ReplicationSlotManagerSpec extends AnyFlatSpec {

  val testSlotName = "test"

  val testSlot = ReplicationSlot(testSlotName)

  val testSlotStatus = new SlotStatus {
    override val slotName: String = testSlotName

    override def isBehind: Boolean = false
  }

  val testReplicationSlotManager = new ReplicationSlotManager {
    override def createReplicationSlot: ReplicationSlot = testSlot

    override def deleteReplicationSlot(slotName: String): Boolean = true
  }.apply()


  "A ReplicationSlotManager" should "create ReplicationSlot instances" in {
    val mockSender = mock[ActorRef[ReplicationSlot]]
    val testKit = BehaviorTestKit(testReplicationSlotManager)
    testKit.run(CreateReplicationSlot(mockSender))
    verify(mockSender).tell(testSlot)
  }

  "A ReplicationSlotManager" should "delete ReplicationSlot instances" in {
    val mockSender = mock[ActorRef[Boolean]]
    val testKit = BehaviorTestKit(testReplicationSlotManager)
    testKit.run(DeleteReplicationSlot(testSlotName, mockSender))
    verify(mockSender).tell(true)
  }
}

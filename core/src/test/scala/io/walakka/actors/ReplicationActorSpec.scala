package io.walakka.actors

import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import io.walakka.actors.Replication.ReplicationSlot
import io.walakka.actors.ReplicationActor.Start
import org.mockito.Mockito.{spy, verify}
import org.scalatest.flatspec.AnyFlatSpec

class ReplicationActorSpec extends AnyFlatSpec {
  val testSlotName = "test"

  "A Replication Actor" should "derive its behavior from its slot" in {
    val slot = spy(ReplicationSlot(testSlotName))
    val replicationActor = ReplicationActor(slot)
    val testKit = BehaviorTestKit(replicationActor)
    testKit.run(Start)
    verify(slot).createBehavior
  }
}

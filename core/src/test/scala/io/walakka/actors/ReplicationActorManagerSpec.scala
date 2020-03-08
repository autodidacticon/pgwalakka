package io.walakka.actors

import akka.actor.testkit.typed.Effect.{Spawned, SpawnedAnonymous, Stopped}
import akka.actor.testkit.typed.scaladsl.BehaviorTestKit
import io.walakka.actors.Replication.ReplicationSlot
import org.scalatest.flatspec.AnyFlatSpec

class ReplicationActorManagerSpec extends AnyFlatSpec {

  val testSlotName = "test"

  val testSlot: ReplicationSlot = ReplicationSlot(testSlotName)

  "A ReplicationActorManager" should "create and delete ReplicationActor instances" in {
    val managerActor = ReplicationActorManager()
    val testKit = BehaviorTestKit(managerActor)
    val testReplicationActor = ReplicationActor(testSlot)
    testKit.run(ReplicationActorManager.CreateReplicationActor(testSlot))
    testKit.expectEffect(Spawned(testReplicationActor, testSlotName))
    testKit.run(ReplicationActorManager.DeleteReplicationActor(testSlot))
    testKit.expectEffect(Stopped(testSlotName))
  }
}

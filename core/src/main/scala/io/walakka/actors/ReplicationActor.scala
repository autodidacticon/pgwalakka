package io.walakka.actors

import akka.actor.typed.Behavior
import io.walakka.actors.Replication.ReplicationSlot

object ReplicationActor {
  sealed trait ReplicationAction
  case object Start extends ReplicationAction

  def apply(slot: ReplicationSlot): Behavior[ReplicationAction] = slot.createBehavior
}

package io.walakka.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import io.walakka.actors.Replication.ReplicationSlot

sealed trait ReplicationSlotCommand

case class CreateReplicationSlot(replyTo: ActorRef[ReplicationSlot]) extends ReplicationSlotCommand

case class DeleteReplicationSlot(slotName: String, replyTo: ActorRef[Boolean]) extends ReplicationSlotCommand

trait ReplicationSlotManager {

  def createReplicationSlot: ReplicationSlot

  def deleteReplicationSlot(slotName: String): Boolean

  def apply(): Behavior[ReplicationSlotCommand] = {
    Behaviors receiveMessage[ReplicationSlotCommand] {
      _ match {
        case CreateReplicationSlot(sender) =>
          sender ! createReplicationSlot
          Behaviors same
        case DeleteReplicationSlot(slotName, sender) =>
          sender ! deleteReplicationSlot(slotName)
          Behaviors same
      }
    }
  }
}

trait SlotStatus {

  val slotName: String

  def isBehind: Boolean

}

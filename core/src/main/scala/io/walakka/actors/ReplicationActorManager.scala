package io.walakka.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import io.walakka.actors.Replication.ReplicationSlot
import io.walakka.actors.ReplicationActor.Start

object ReplicationActorManager {
  sealed trait ReplicationActorCommand
  case class CreateReplicationActor(slot: ReplicationSlot) extends ReplicationActorCommand

  case class DeleteReplicationActor(slot: ReplicationSlot) extends ReplicationActorCommand

  def apply(): Behavior[ReplicationActorCommand] =
    Behaviors receive[ReplicationActorCommand] {
      (context, message) =>
        message match {
          case create: CreateReplicationActor =>
            context.spawn(ReplicationActor(create.slot), create.slot.name).tell(Start)
            Behaviors same
          case delete: DeleteReplicationActor =>
            context.child(delete.slot.name).foreach(context stop _)
            Behaviors same
        }
    }
}

object Replication  {
  class ReplicationSlot(val name: String) {
    def createBehavior: Behavior[ReplicationActor.ReplicationAction] = NoopBehavior()
  }

  object ReplicationSlot {
    def apply(name: String) = new ReplicationSlot(name)
  }

  object NoopBehavior {
    def apply(): Behavior[ReplicationActor.ReplicationAction] = Behaviors.ignore
  }
}

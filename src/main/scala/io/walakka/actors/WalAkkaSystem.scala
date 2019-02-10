package io.walakka.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import scala.concurrent.duration._

trait WalAkkaSystem {
  val actorSystem: ActorSystem

  final val ManagerName = "manager"

  implicit val timeout = Timeout(5 seconds)

  def createManagerActor(replicationOptions: ReplicationOptions): ActorRef =
    actorSystem.actorOf(
      ManagerActor
        .props(replicationOptions),
      ManagerName)
}

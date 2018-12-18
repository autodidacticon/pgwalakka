package io.walakka.actors

import akka.actor.{ActorRef, ActorSystem, Props}

trait ActorManager {
  val actorSystem: ActorSystem

  val replicationThreshold: Long
  
  val slotOptions: Map[String, String]

  val statusIntervalMs: Int

  def createManagerActor: ActorRef = actorSystem.actorOf(Props(new ReplicationActorManagerActor(replicationThreshold, slotOptions, statusIntervalMs)))
}

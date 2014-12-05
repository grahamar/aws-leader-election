package com.teambytes.awsleader

import akka.actor.{Props, Actor, ActorLogging}

class LeaderActor(handler: LeaderActionsHandler) extends Actor with ActorLogging {

  override def preStart() = {
    log.info("LeaderActor: Starting.")
    handler.onIsLeader()
  }

  override def postStop() = {
    handler.onIsNotLeader()
    log.info("LeaderActor: Stopped.")
  }

  override def receive: Receive = {
    case _ =>
      // Do nothing, we only care about leader election
  }

}

object LeaderActor {
  def props(handler: LeaderActionsHandler) = Props(classOf[LeaderActor], handler)
}

package com.teambytes.awsleader.test.util

import akka.actor.{ActorRef, Actor, Props}

object WrapperActor{
  def props(target: ActorRef) = Props(classOf[WrapperActor], target)
}

class WrapperActor (target: ActorRef) extends Actor {
  def receive = {
    case x => target forward x
  }
}

package com.teambytes.awsleader.test.util

import akka.actor.{Actor, Address}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberRemoved

class ClusterMemberLeaves(address: Address) extends Actor{
  private val cluster = Cluster(context.system)

  override def preStart() = cluster.subscribe(self, classOf[MemberRemoved])
  override def postStop() = cluster.unsubscribe(self)

  private var result = false

  def receive = {
    case MemberRemoved(member, _) if member.address == address => result = true
    case "HASLEFT" => sender ! result
  }
}

package com.teambytes.awsleader.test.util

import akka.actor.{Actor, Address}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ClusterDomainEvent, CurrentClusterState, MemberUp}

class ClusterMemberUp(address: Address) extends Actor {
  private val cluster = Cluster(context.system)

  override def preStart() = cluster.subscribe(self, classOf[ClusterDomainEvent])
  override def postStop() = cluster.unsubscribe(self)

  private var result = false

  def receive = {
    case state: CurrentClusterState => result = state.members.filterNot(_.status == MemberUp).map(_.address).contains(address)
    case MemberUp(member) if member.address == address => result = true
    case "ISUP" => sender ! result
  }
}

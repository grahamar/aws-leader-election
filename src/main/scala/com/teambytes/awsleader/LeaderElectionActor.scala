package com.teambytes.awsleader

import akka.actor._
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, CurrentClusterState, ClusterDomainEvent}
import akka.cluster.{MemberStatus, Cluster, Member}
import com.teambytes.awsleader.LeaderElectionActor.{Data, State}

private[awsleader] class LeaderElectionActor(minMembers: Int, leaderProp: () => Props) extends Actor with FSM[State, Data] with ActorLogging {
  import LeaderElectionActor._

  startWith(NoQuorum, Data(None, Set()))

  private val cluster = Cluster(context.system)
  override def preStart() = {
    log.info("LeaderElectionActor: Starting.")
    cluster.subscribe(self, classOf[ClusterDomainEvent])
  }

  override def postStop() = {
    cluster.unsubscribe(self)
    log.info("LeaderElectionActor: Stopped.")
  }

  when(NoQuorum) {
    case e@Event(s:CurrentClusterState, d: Data) => stayOrGoToQuorum(d.copy(clusterMembers = s.members.filter(_.status == MemberStatus.Up)))
    case e@Event(MemberUp(member), d: Data) => stayOrGoToQuorum(d.copy(clusterMembers = d.clusterMembers + member))
    case e@Event(MemberRemoved(member, previousStatus), d: Data) => stayOrGoToQuorum(d.copy(clusterMembers = d.clusterMembers - member))
  }

  when(Quorum) {
    case e@Event(s:CurrentClusterState, d: Data) => stayOrGoToNoQuorum(d.copy(clusterMembers = s.members.filter(_.status == MemberStatus.Up)))
    case e@Event(MemberUp(member), d: Data) => stayOrGoToNoQuorum(d.copy(clusterMembers = d.clusterMembers + member))
    case e@Event(MemberRemoved(member, previousStatus), d: Data) => stayOrGoToNoQuorum(d.copy(clusterMembers = d.clusterMembers - member))
  }

  whenUnhandled {
    case e@Event(c:ClusterDomainEvent, d: Data) => stay using d
  }

  private def stayOrGoToQuorum(newData: Data) =
    if (newData.numberOfMembers() >= minMembers){
      log.info("LeaderElectionActor: Quorum has been achieved. Current members: {}", newData.clusterMembers)
      goto(Quorum) using newData.copy(target = Some(context.actorOf(leaderProp(), "worker")))
    } else {
      log.info("LeaderElectionActor: Quorum has not been reached. Current members: {}", newData.clusterMembers)
      stay using newData
    }

  private def stayOrGoToNoQuorum(newData: Data) =
    if (newData.numberOfMembers() < minMembers) {
      log.info("LeaderElectionActor: Quorum has been lost. Current members: {}", newData.clusterMembers)
      newData.target.foreach(_ ! PoisonPill)
      goto(NoQuorum) using newData.copy(target = None)
    } else {
      log.info("LeaderElectionActor: Still have quorum. Current members: {}", newData.clusterMembers)
      stay using newData
    }

}

object LeaderElectionActor {
  def props(handler: LeaderActionsHandler, minMembers: Int) =
    Props(classOf[LeaderElectionActor], minMembers, LeaderActor.props(handler))

  // states
  private[awsleader] sealed trait State
  private[awsleader] case object NoQuorum extends State
  private[awsleader] case object Quorum extends State

  private[actors] case class Data(target: Option[ActorRef], clusterMembers: Set[Member]){
    def numberOfMembers() = clusterMembers.size
  }

}

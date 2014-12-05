package com.teambytes.awsleader.test

import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.{TestActorRef, TestProbe, ImplicitSender}
import java.util.UUID
import akka.actor._
import com.teambytes.awsleader.LeaderElectionActor
import com.teambytes.awsleader.test.util._
import scala.concurrent.Await
import akka.actor.ActorIdentity
import akka.actor.Identify
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import com.typesafe.config.ConfigFactory
import akka.cluster.Cluster

class LeaderElectionActorSpecMultiJvmNode1 extends LeaderElectionActorSpec
class LeaderElectionActorSpecMultiJvmNode2 extends LeaderElectionActorSpec
class LeaderElectionActorSpecMultiJvmNode3 extends LeaderElectionActorSpec

/**
 * NOTE: A node in a cluster can only leave once.
 */
abstract class LeaderElectionActorSpec extends MultiNodeSpec(LEAClusterConfig) with STMultiNodeSpec with ImplicitSender {

  implicit val timeout = Timeout(5 second)

  def initialParticipants = roles.size

  def leaderProps(factor: SeqRefsFactor) = () => factor.next()

  "Leader Election actor" should {
    "not start any actor when quorum not reached" in {
      runOn(LEAClusterConfig.node1) {
        enterBarrier("deployed")
        val leader1 = TestProbe()
        val leaderFactory = new SeqRefsFactor(List(WrapperActor.props(leader1.ref)))
        val leaderElectionActorName = UUID.randomUUID().toString
        val actor = TestActorRef(Props(classOf[LeaderElectionActor], 10, leaderProps(leaderFactory)), leaderElectionActorName)
        vertifyActorDoesNotExistsOnPath(s"akka.tcp://LeaderElectionActorSpec@localhost:23456/user/$leaderElectionActorName/leader")
        expectNoMsg(1.second)
        actor ! PoisonPill
      }

      runOn(LEAClusterConfig.node2, LEAClusterConfig.node3) { enterBarrier("deployed")}
      enterBarrier("finished")
    }

    "start leader when quorum satisfied on initial CurrentClusterState" in {
      runOn(LEAClusterConfig.node1) {
        enterBarrier("deployed")

        val leader1 = TestProbe()
        val leaderFactory = new SeqRefsFactor(List(WrapperActor.props(leader1.ref)))

        val leaderElectionActorName = UUID.randomUUID().toString
        val actor = TestActorRef(Props(classOf[LeaderElectionActor], 1, leaderProps(leaderFactory)), leaderElectionActorName)

        vertifyActorExistsOnPath(s"akka.tcp://LeaderElectionActorSpec@localhost:23456/user/$leaderElectionActorName/leader")
        actor ! PoisonPill

        enterBarrier("clean")
      }

      runOn(LEAClusterConfig.node2, LEAClusterConfig.node3) {
        enterBarrier("deployed")
        enterBarrier("clean")
      }
      enterBarrier("finished")
    }

    "start leader when Quorum satisfied when additional node joins" in {
      runOn(LEAClusterConfig.node1) {
        enterBarrier("deployed")

        val leader1 = TestProbe()
        val leaderFactory = new SeqRefsFactor(List(WrapperActor.props(leader1.ref)))

        val leaderElectionActorName = UUID.randomUUID().toString
        val actor = TestActorRef(Props(classOf[LeaderElectionActor], 2, leaderProps(leaderFactory)), leaderElectionActorName)

        vertifyActorDoesNotExistsOnPath(s"akka.tcp://LeaderElectionActorSpec@localhost:23456/user/$leaderElectionActorName/leader")

        enterBarrier("addNodeTwo")
        enterBarrier("nodeTwoAdded")

        vertifyActorExistsOnPath(s"akka.tcp://LeaderElectionActorSpec@localhost:23456/user/$leaderElectionActorName/leader")
        actor ! PoisonPill
      }

      runOn(LEAClusterConfig.node2) {
        enterBarrier("deployed")
        enterBarrier("addNodeTwo")
        nodeUp(node(LEAClusterConfig.node2).address)
        enterBarrier("nodeTwoAdded")
      }

      runOn(LEAClusterConfig.node3) {
        enterBarrier("deployed")
        enterBarrier("addNodeTwo")
        enterBarrier("nodeTwoAdded")
      }

      enterBarrier("finished")
    }

    "terminate leader when Quorum is lost" in {
      runOn(LEAClusterConfig.node1) {
        enterBarrier("deployed")

        val leader1 = TestProbe()
        val leaderFactory = new SeqRefsFactor(List(WrapperActor.props(leader1.ref)))

        val leaderElectionActorName = UUID.randomUUID().toString
        val actor = TestActorRef(Props(classOf[LeaderElectionActor], 2, leaderProps(leaderFactory)), leaderElectionActorName)

        vertifyActorExistsOnPath(s"akka.tcp://LeaderElectionActorSpec@localhost:23456/user/$leaderElectionActorName/leader")

        enterBarrier("removeNodeTwo")
        enterBarrier("nodeTwoRemoved")

        vertifyActorDoesNotExistsOnPath(s"akka.tcp://LeaderElectionActorSpec@localhost:23456/user/$leaderElectionActorName/leader")

        actor ! PoisonPill

        enterBarrier("clean")
      }

      runOn(LEAClusterConfig.node2) {
        nodeUp(node(LEAClusterConfig.node2).address)
        enterBarrier("deployed")
        enterBarrier("removeNodeTwo")
        downNode(node(LEAClusterConfig.node2).address)
        Thread.sleep(2000)
        enterBarrier("nodeTwoRemoved")
        enterBarrier("clean")
      }

      runOn(LEAClusterConfig.node3) {
        enterBarrier("deployed")
        enterBarrier("removeNodeTwo")
        enterBarrier("nodeTwoRemoved")
        enterBarrier("clean")
      }

      enterBarrier("finished")
    }
  }


  private def nodeUp(address: Address): Unit = {
    import akka.pattern._
    Cluster(system).join(node(LAClusterConfig.node1).address)
    val ref = system.actorOf(Props(classOf[ClusterMemberUp], address))
    awaitCond(Await.result((ref ? "ISUP").mapTo[Boolean], 1 second))
    ref ! PoisonPill
  }

  private def downNode(address: Address) = {
    import akka.pattern._
    val ref = system.actorOf(Props(classOf[ClusterMemberLeaves], address))
    Cluster(system).leave(address)
    awaitCond(Await.result((ref ? "HASLEFT").mapTo[Boolean], 1 second))
    ref ! PoisonPill
  }

  private def vertifyActorExistsOnPath(path: String) {
    import akka.pattern._
    import scala.concurrent.ExecutionContext.Implicits.global
    awaitCond(Await.result((system.actorSelection(path) ? Identify(None)).mapTo[ActorIdentity].map(_.ref.isDefined), 1 seconds), 2 seconds)
  }

  private def vertifyActorDoesNotExistsOnPath(path: String) {
    import akka.pattern._
    import scala.concurrent.ExecutionContext.Implicits.global
    awaitCond(Await.result((system.actorSelection(path) ? Identify(None)).mapTo[ActorIdentity].map(_.ref == None), 1 seconds), 6 seconds)
  }

}

object LEAClusterConfig extends MultiNodeConfig with ClusterConfig {
  val seed = s"akka.tcp://LeaderElectionActorSpec@localhost:23456"
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  nodeConfig(node1)(ConfigFactory.parseString(config(23456, seed)))
  nodeConfig(node2)(ConfigFactory.parseString(configNoSeeds(23457)))
  nodeConfig(node3)(ConfigFactory.parseString(configNoSeeds(23458)))
}

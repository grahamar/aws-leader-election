package com.teambytes.awsleader.test

import akka.testkit.{TestActorRef, ImplicitSender}
import akka.actor._
import com.teambytes.awsleader.{LeaderActionsHandler, LeaderActor}
import org.scalatest.mock.MockitoSugar
import util._
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import java.util.UUID
import scala.concurrent.duration._
import akka.util.Timeout
import scala.language.postfixOps
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito._

class LeaderActorSpecMultiJvmNode1 extends LeaderActorSpec
class LeaderActorSpecMultiJvmNode2 extends LeaderActorSpec
class LeaderActorSpecMultiJvmNode3 extends LeaderActorSpec

abstract class LeaderActorSpec extends MultiNodeSpec(LAClusterConfig) with STMultiNodeSpec with ImplicitSender with MockitoSugar {

  implicit val timeout = Timeout(5 second)

  def initialParticipants = roles.size

  def newHandler = mock[LeaderActionsHandler]

  "Leader actor" should {
    "call leader action handler after first sync" in {
      runOn(LAClusterConfig.node1) {
        enterBarrier("deployed")

        val handler = newHandler
        val leaderActorName = UUID.randomUUID().toString
        val actor = TestActorRef(Props(classOf[LeaderActor], handler), leaderActorName)

        expectNoMsg(1.second)

        verify(handler).onIsLeader

        actor ! PoisonPill

        verify(handler).onIsNotLeader
      }

      runOn(LAClusterConfig.node2, LAClusterConfig.node3) { enterBarrier("deployed") }
      enterBarrier("finished")
    }

  }

}

object LAClusterConfig extends MultiNodeConfig with ClusterConfig {
  val seed = s"akka.tcp://LeaderActorSpec@localhost:33456"
  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  nodeConfig(node1)(ConfigFactory.parseString(config(33456, seed)))
  nodeConfig(node2)(ConfigFactory.parseString(config(33457, seed)))
  nodeConfig(node3)(ConfigFactory.parseString(configNoSeeds(33458)))
}

package com.teambytes.awsleader

import akka.actor.{ActorSystem, PoisonPill}
import akka.contrib.pattern.ClusterSingletonManager
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object AwsLeaderElection {

  def startLeaderElection(handler: LeaderActionsHandler)(implicit ec: ExecutionContext): Unit =
    new AwsLeaderElection(handler, AkkaConfig.apply())(ec)

  def startLeaderElection(handler: LeaderActionsHandler, defaults: Config)(implicit ec: ExecutionContext): Unit =
    new AwsLeaderElection(handler, AkkaConfig(defaults))(ec)

}

class AwsLeaderElection(handler: LeaderActionsHandler, akkaConfig: AkkaConfig)(implicit ec: ExecutionContext) {

  private val logger = LoggerFactory.getLogger(classOf[AwsLeaderElection])

  logger.info("Loading leader election system...")
  logger.info(s"Seeds: ${akkaConfig.seeds}")

  private val clusterSystem = ActorSystem("aws-leader-election-cluster", akkaConfig.config)

  clusterSystem.actorOf(ClusterSingletonManager.props(
    singletonProps = LeaderElectionActor.props(handler, akkaConfig.seeds.size),
    singletonName = "consumer",
    terminationMessage = PoisonPill,
    role = Some("worker")),
    name = "singleton"
  )

  logger.info("Leader election started!")

}
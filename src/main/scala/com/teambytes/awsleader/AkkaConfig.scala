package com.teambytes.awsleader

import com.amazonaws.auth._
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient
import com.amazonaws.services.ec2.AmazonEC2Client
import com.typesafe.config.{Config, ConfigValueFactory, ConfigFactory}
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import scala.util.Try

private[awsleader] object AkkaConfig {

  def apply(defaults: Config = ConfigFactory.load()) = new AkkaConfig(defaults)

  /**
   * Create a credentials provider, based on configured access and secret keys
   *
   * If the keys are both set to "from-classpath", the provider will
   * look for a properties file in the classpath that contains properties
   * named access-key and secret-key.
   *
   * @param accessKey the configured accessKey, or the 'from-classpath' string
   * @param secretKey the configured secretKey, or the 'from-classpath' string
   * @return an AWSCredentialsProvider wrapping the configured keys
   */
  def createAwsCredentialsProvider(accessKey: String, secretKey: String): AWSCredentialsProvider = {

    def isClasspath(key: String) = "from-classpath".equals(key)

    if (isClasspath(accessKey) && isClasspath(secretKey)) {
      new ClasspathPropertiesFileCredentialsProvider()
    } else if (isClasspath(accessKey) || isClasspath(secretKey)) {
      throw new RuntimeException("Both AWS credentials 'aws.credentials.access-key' and 'aws.credentials.secret-key' must be 'from-classpath' or neither.")
    } else new AWSCredentialsProvider {
      override def getCredentials: AWSCredentials = new BasicAWSCredentials(accessKey, secretKey)
      override def refresh(): Unit = {}
    }
  }

}

private[awsleader] class AkkaConfig(defaults: Config) {

  private lazy val logger = LoggerFactory.getLogger(getClass)

  private val local = Try(defaults.getBoolean("aws.leader.local")).getOrElse(false)
  private val defaultPort = defaults.getString("akka.port")

  private lazy val ec2 = {
    val credentials = AkkaConfig.createAwsCredentialsProvider(
      defaults.getString("aws.credentials.access-key"),
      defaults.getString("aws.credentials.secret-key")
    )
    val scalingClient = new AmazonAutoScalingClient(credentials)
    val ec2Client = new AmazonEC2Client(credentials)
    logger.debug("Creating EC2 client")
    new EC2(scalingClient, ec2Client)
  }

  private val (host, siblings, port) = {
    if (local) {
      logger.info("Running with local configuration")
      val nodes = defaults.getStringList("akka.cluster.seed-nodes").asScala
      val localPort = defaults.getString("akka.remote.netty.tcp.port")
      ("localhost", nodes, localPort)
    } else {
      logger.info("Using EC2 autoscaling configuration")
      (ec2.currentIp, ec2.siblingIps, defaultPort)
    }
  }

  val seeds = siblings.map { ip =>
    if(local) {
      logger.debug(s"Adding seed node: $ip")
      ip
    } else {
      val add = s"akka.tcp://aws-leader-election-cluster@$ip:$defaultPort"
      logger.debug(s"Adding seed node: $add")
      add
    }
  }

  private val overrideConfig =
    ConfigFactory.empty()
      .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(host))
      .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(port))
      .withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(seeds.asJava))
      .withValue("akka.cluster.min-nr-of-members", ConfigValueFactory.fromAnyRef(seeds.size))

  val config = {
    if(local) defaults.withValue("akka.cluster.min-nr-of-members", ConfigValueFactory.fromAnyRef(seeds.size)) else overrideConfig.withFallback(defaults)
  }

}
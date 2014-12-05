aws-leader-election
==========

A Scala & Akka-Cluster based PnP leader election library for use in AWS, built on [Akka](http://akka.io/) & using the akka-cluster cluster singleton to perform leader elected tasks.

Designed for use in a distributed Play application, deployed in an auto-scaling cluster in Amazon EC2 (**in a single region only**).
Uses AWS's auto-scaling java client to discover EC2 instances and creates an akka-cluster from the auto-scaling group members.

Settings
-------
Settings read from `application.conf` at the root of the classpath, or as JVM run parameters.
- akka.port - The port to look for akka-cluster instances on.
- aws.leader.local - `true` to run locally and bypass AWS discovery.
- aws.credentials - `access-key` & `secret-key` to pass to the AWS client for EC2 instance discovery

Example Usage (Play Application)
-------

### Global.scala

    import com.teambytes.awsleader.Inflatable
    
    object Global extends play.api.GlobalSettings {
    
      implicit val ec = play.api.libs.concurrent.Execution.defaultContext
    
      override def onStart(app: play.api.Application) = {
        Inflatable.startLeaderElection(new TestLeaderElectionHandler(), app.configuration.underlying)
      }
    
    }
    
### TestLeaderElectionHandler.scala

    import java.util.concurrent.{Executors, SchedulingLeaderActionsHandler}
    
    import com.teambytes.awsleader.{PeriodicTask, SchedulingInflatableLeader}
    import play.core.NamedThreadFactory
    
    class TestLeaderElectionHandler extends SchedulingLeaderActionsHandler {
    
      val tasks = Set(new TestPeriodicJob())
    
      override def leaderExecutor: ScheduledExecutorService = Executors.newScheduledThreadPool(1, new NamedThreadFactory("test-job"))
    
      override def leaderTasks: Iterable[PeriodicTask] = tasks
    }

### TestPeriodicJob.scala

    import com.teambytes.awsleader.PeriodicTask
    import play.api.Logger
    
    class TestPeriodicJob extends PeriodicTask {
    
      import scala.concurrent.duration._
    
      override def periodMs: Long = 5.seconds.toMillis
    
      override def run(): Unit = {
        Logger.info(
          """
            |
            |
            |
            |
            |Running test periodic job here and now!!!!!!!
            |
            |
            |
            |
          """.stripMargin)
      }
    }

Running Locally
-------

When running locally, make sure to provide `-Daws.leader.local=true`

License
-------

*Apache 2.0*

Links & kudos
-------------

* [akka-ec2 - Example setup of an Akka cluster in an Amazon EC2 AutoScaling group](https://github.com/chrisloy/akka-ec2)

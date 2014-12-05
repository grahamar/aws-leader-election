import sbtrelease._
import ReleaseStateTransformations._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

lazy val publishSignedAction = { st: State =>
  val extracted = Project.extract(st)
  val ref = extracted.get(thisProjectRef)
  extracted.runAggregated(com.typesafe.sbt.pgp.PgpKeys.publishSigned in Global in ref, st)
}

lazy val root = (project in file(".")).
  settings(releaseSettings: _*).
  settings(SbtMultiJvm.multiJvmSettings: _*).
  settings(
    name := "aws-leader-election",
    organization := "com.teambytes",
    scalaVersion := "2.11.4",
    crossScalaVersions := Seq("2.11.4", "2.10.4"),
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "1.7.7",
      "com.typesafe.akka" %% "akka-actor" % "2.3.7",
      "com.typesafe.akka" %% "akka-cluster" % "2.3.7",
      "com.typesafe.akka" %% "akka-contrib" % "2.3.7",
      "com.typesafe.akka" %% "akka-slf4j" % "2.3.7",
      "com.amazonaws" % "aws-java-sdk" % "1.9.8",
      "ch.qos.logback" % "logback-classic" % "1.1.2" % "test",
      "com.typesafe.akka" %% "akka-testkit" % "2.3.7" % "test",
      "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.3.7" % "test",
      "org.scalatest" %% "scalatest" % "2.2.2" % "test"
    ),
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target,
    // and combine the results from ordinary test and multi-jvm tests
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults)  =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    },
    publishArtifact in Test := false,
    publishMavenStyle := true,
    pomIncludeRepository := { _ => false },
    licenses := Seq("Apache License 2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
    homepage := Some(url("https://github.com/grahamar/aws-leader-election")),
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    pomExtra := <scm>
      <url>git@github.com:grahamar/aws-leader-election.git</url>
      <connection>scm:git:git@github.com:grahamar/aws-leader-election.git</connection>
    </scm>
      <developers>
        <developer>
          <id>grhodes</id>
          <name>Graham Rhodes</name>
          <url>https://github.com/grahamar</url>
        </developer>
      </developers>,
    sbtrelease.ReleasePlugin.ReleaseKeys.releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts.copy(action = publishSignedAction),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
).configs(MultiJvm)

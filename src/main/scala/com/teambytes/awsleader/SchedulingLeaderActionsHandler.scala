package com.teambytes.awsleader

import java.util.concurrent.{TimeUnit, ScheduledFuture, ScheduledExecutorService}

import org.slf4j.LoggerFactory

trait SchedulingLeaderActionsHandler extends LeaderActionsHandler {

  private lazy val log = LoggerFactory.getLogger(getClass)

  def leaderExecutor: ScheduledExecutorService

  def leaderTasks: Iterable[PeriodicTask]

  val interruptTaskThreads = false

  // future around the periodic task, if we are leader & replicating
  @volatile private var scheduledFutures: Iterable[ScheduledFuture[_]] = Iterable.empty

  override def onIsLeader(): Unit = {
    // Only schedule tasks if we're not already the leader
    if (scheduledFutures.isEmpty) {
      // There was some disagreement on this. I think this is right. If we start to run slow,
      // we decrease the time between invocations to try to catch up. That feels better to me
      // than, "we start to get slow and still wait 1 second before running again". It should
      // speed up in that case.
      log.info("Starting leader tasks")
      scheduledFutures = leaderTasks.map(task => leaderExecutor.scheduleAtFixedRate(task, task.initialDelayMs, task.periodMs, TimeUnit.MILLISECONDS))
    }
  }

  override def onIsNotLeader(): Unit = {
    log.info("Stopping leader tasks")
    scheduledFutures.map(_.cancel(interruptTaskThreads))
    scheduledFutures = Iterable.empty
  }

}

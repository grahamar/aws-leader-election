package com.teambytes.awsleader

/**
 * Runnable Task that can be run periodically, with the given rate in milli seconds
 */
trait PeriodicTask extends Runnable {

  /**
   * The rate at which this task is executed while the node is leader
   */
  def periodMs: Long

  /**
   * The initial delay before this task is run for the first time after the node has become leader
   */
  def initialDelayMs: Long = 0

}

package com.teambytes.awsleader

trait LeaderActionsHandler {

  def onIsLeader(): Unit

  def onIsNotLeader(): Unit

}

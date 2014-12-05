package com.teambytes.awsleader.test.util

import akka.actor.Props

class SeqRefsFactor(props: List[Props]) {
  private var index = 0

  def next(batchSize: Int):Props = {
    assert(batchSize == 10)
    next()
  }

  def next():Props = {
    val actorRef = props(index)
    index += 1
    actorRef
  }
}

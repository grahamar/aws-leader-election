package com.teambytes.awsleader.test.util

import akka.remote.testkit.MultiNodeSpecCallbacks
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

trait STMultiNodeSpec extends MultiNodeSpecCallbacks with WordSpecLike with BeforeAndAfterAll {

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()
}

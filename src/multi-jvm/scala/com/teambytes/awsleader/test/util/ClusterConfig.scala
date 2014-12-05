package com.teambytes.awsleader.test.util

trait ClusterConfig {

  def config(port: Int, seed: String) =
    s"""
      akka {
        actor {
          provider = "akka.cluster.ClusterActorRefProvider"
        }
        remote {
          netty.tcp {
            host = 'localhost'
            port = $port
          }
        }
        cluster {
          seed-nodes = [
            "$seed"
          ]
        }
      }"""

  def configNoSeeds(port: Int) =
    s"""
      akka {
        actor {
          provider = "akka.cluster.ClusterActorRefProvider"
        }
        remote {
          netty.tcp {
            host = 'localhost'
            port = $port
          }
        }
      }"""

}

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.bwsw.tstreams.coordination.client

import java.net.{DatagramSocket, InetAddress}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

import com.bwsw.tstreamstransactionserver.protocol.TransactionState
import org.apache.curator.framework.CuratorFramework
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

object UdpEventsBroadcastClient {
  val logger = LoggerFactory.getLogger(this.getClass)
}

/**
  *
  * @param curatorClient
  * @param partitions
  */
class UdpEventsBroadcastClient(curatorClient: CuratorFramework, prefix: String, partitions: Set[Int]) {

  private val UPDATE_PERIOD_MS = 1000

  private val isStopped = new AtomicBoolean(true)

  private val clientSocket = new DatagramSocket()

  private val partitionSubscribers = new java.util.concurrent.ConcurrentHashMap[Int, Set[String]]()

  val startUpdateThreadLatch = new CountDownLatch(1)

  private val updateThread = new Thread(() => {
    while (!isStopped.get()) {
      partitions.foreach(p => updateSubscribers(p))
      startUpdateThreadLatch.countDown()
      Thread.sleep(UPDATE_PERIOD_MS)
    }
  })

  /**
    * Initialize coordinator
    */
  def init(): Unit = {
    isStopped.set(false)

    partitions.foreach { p => {
      partitionSubscribers.put(p, Set[String]().empty)
      updateSubscribers(p)
    }
    }
    updateThread.start()
    startUpdateThreadLatch.await()
  }

  private def broadcast(set: Set[String], msg: TransactionState): Unit = {
    if(!set.isEmpty) {
      val bytes = msg.toByteArray

      if (UdpEventsBroadcastClient.logger.isDebugEnabled())
        UdpEventsBroadcastClient.logger.debug(s"Producer Broadcast Set Is: ${set}")

      set.foreach(address => {
        val splits = address.split(":")
        val host = InetAddress.getByName(splits(0))
        val port = splits(1).toInt
        val sendPacket = new java.net.DatagramPacket(bytes, bytes.length, host, port)
        var isSent = false
        while (!isSent) {
          try {
            clientSocket.send(sendPacket)
            isSent = true
          } catch {
            case e: Exception =>
              UdpEventsBroadcastClient.logger.warn(s"Send $msg to $host:$port failed. Exception is: $e")
          }
        }
      })
    }
  }

  /**
    * Publish Message to all accepted subscribers
    *
    * @param msg Message
    */
  def publish(msg: TransactionState): Unit = {
    if (!isStopped.get) {
      val set = partitionSubscribers.get(msg.partition)
      broadcast(set, msg)
    }
  }

  /**
    * Update subscribers on specific partition
    */
  private def updateSubscribers(partition: Int) = {
    if (curatorClient.checkExists.forPath(s"$prefix/subscribers/$partition") != null) {
      val newPeers = curatorClient.getChildren.forPath(s"$prefix/subscribers/$partition").asScala.toSet
      partitionSubscribers.put(partition, newPeers)
    }
  }

  /**
    * Stop this Subscriber client
    */
  def stop() = {
    if (isStopped.getAndSet(true))
      throw new IllegalStateException("Producer->Subscriber notifier was stopped second time.")

    updateThread.join()
    clientSocket.close()
    partitionSubscribers.clear()
  }
}

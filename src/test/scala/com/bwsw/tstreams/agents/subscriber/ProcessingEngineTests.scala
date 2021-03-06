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

package com.bwsw.tstreams.agents.subscriber

import com.bwsw.tstreams.agents.consumer.subscriber.{Callback, ProcessingEngine, QueueBuilder}
import com.bwsw.tstreams.agents.consumer.{ConsumerTransaction, TransactionOperator}
import com.bwsw.tstreams.testutils.IncreasingGenerator
import com.bwsw.tstreamstransactionserver.rpc.TransactionStates
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable.ListBuffer


/**
  * Created by Ivan Kudryavtsev on 22.08.16.
  */

class ProcessingEngineOperatorTestImpl extends TransactionOperator {

  val TOTAL = 10
  val transactions = new ListBuffer[ConsumerTransaction]()
  for (i <- 0 until TOTAL)
    transactions += new ConsumerTransaction(0, IncreasingGenerator.get, 1, TransactionStates.Checkpointed, -1)

  var lastTransaction: Option[ConsumerTransaction] = None

  override def getLastTransaction(partition: Int): Option[ConsumerTransaction] = lastTransaction

  override def getTransactionById(partition: Int, id: Long): Option[ConsumerTransaction] = None

  override def setStreamPartitionOffset(partition: Int, id: Long): Unit = {}

  override def loadTransactionFromDB(partition: Int, transactionID: Long): Option[ConsumerTransaction] = None

  override def checkpoint(): Unit = {}

  override def getPartitions(): Set[Int] = Set[Int](0)

  override def getCurrentOffset(partition: Int): Long = IncreasingGenerator.get

  override def buildTransactionObject(partition: Int, id: Long, state: TransactionStates, count: Int): Option[ConsumerTransaction] = None

  override def getTransactionsFromTo(partition: Int, from: Long, to: Long): ListBuffer[ConsumerTransaction] = transactions

  override def getProposedTransactionId(): Long = IncreasingGenerator.get
}

class ProcessingEngineTests extends FlatSpec with Matchers {

  val cb = new Callback {
    override def onTransaction(consumer: TransactionOperator, transaction: ConsumerTransaction): Unit = {}
  }
  val qb = new QueueBuilder.InMemory()


  "constructor" should "create Processing engine" in {
    val pe = new ProcessingEngine(new ProcessingEngineOperatorTestImpl(), Set[Int](0), qb, cb, 100)
  }

  "handleQueue" should "do nothing if there is nothing in queue" in {
    val c = new ProcessingEngineOperatorTestImpl()
    val pe = new ProcessingEngine(c, Set[Int](0), qb, cb, 100)
    val act1 = pe.getLastPartitionActivity(0)
    pe.processReadyTransactions(10)
    val act2 = pe.getLastPartitionActivity(0)
    act1 shouldBe act2
  }

  "handleQueue" should "do fast/full load if there is seq in queue" in {
    val c = new ProcessingEngineOperatorTestImpl()
    val pe = new ProcessingEngine(c, Set[Int](0), qb, cb, 100)
    c.lastTransaction = Option[ConsumerTransaction](new ConsumerTransaction(0, IncreasingGenerator.get, 1, TransactionStates.Checkpointed, -1))
    pe.enqueueLastPossibleTransactionState(0)
    val act1 = pe.getLastPartitionActivity(0)
    Thread.sleep(10)
    pe.processReadyTransactions(10)
    val act2 = pe.getLastPartitionActivity(0)
    act2 - act1 > 0 shouldBe true
  }


}

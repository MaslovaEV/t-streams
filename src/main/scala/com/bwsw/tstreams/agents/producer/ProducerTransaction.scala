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

package com.bwsw.tstreams.agents.producer

import com.bwsw.tstreams.agents.group.ProducerTransactionState
import com.bwsw.tstreamstransactionserver.protocol.TransactionState

/**
  * Created by Ivan Kudryavtsev on 29.08.16.
  */
trait ProducerTransaction {

  def send(obj: Array[Byte]): ProducerTransaction

  def finalizeDataSend(): Unit

  def cancel(): Unit

  def checkpoint(): Unit

  def getStateInfo(status: TransactionState.Status): ProducerTransactionState

  private[tstreams] def getUpdateInfo: Option[RPCProducerTransaction]

  private[tstreams] def getCancelInfoAndClose: Option[RPCProducerTransaction]

  private[tstreams] def notifyCancelEvent()

  private[tstreams] def notifyUpdate(): Unit

  def isClosed: Boolean

  def getTransactionID: Long

  def markAsClosed(): Unit
}

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

package ut

import com.bwsw.tstreamstransactionserver.netty.server.transactionMetadataService.{ProducerTransactionKey, ProducerTransactionValue}
import org.scalatest.{FlatSpec, Matchers}
import com.bwsw.tstreamstransactionserver.rpc.TransactionStates

class TransactionMetaServiceTest
  extends FlatSpec
    with Matchers {

  "Key" should "be serialized/deserialized" in {
    val key = ProducerTransactionKey(1, 10, 15L)
    ProducerTransactionKey.fromByteArray(key.toByteArray) shouldBe key
  }

  it should "be serialized/deserialized with negative stream" in {
    val key = ProducerTransactionKey(-1, 10, 15L)
    ProducerTransactionKey.fromByteArray(key.toByteArray) shouldBe key
  }

  it should "be serialized/deserialized with negative partition" in {
    val key = ProducerTransactionKey(1, -10, 15L)
    ProducerTransactionKey.fromByteArray(key.toByteArray) shouldBe key
  }

  it should "be serialized/deserialized with negative transaction" in {
    val key = ProducerTransactionKey(1, 10, -15L)
    ProducerTransactionKey.fromByteArray(key.toByteArray) shouldBe key
  }

  "ProducerTransaction" should "be serialized/deserialized" in {
    val producerTransaction = ProducerTransactionValue(TransactionStates.Opened, 10, Long.MaxValue, Long.MaxValue)
    ProducerTransactionValue.fromByteArray(producerTransaction.toByteArray) shouldBe producerTransaction
  }

  it should "be serialized/deserialized with negative quantity" in {
    val producerTransaction = ProducerTransactionValue(TransactionStates.Opened, -10, Long.MaxValue, Long.MaxValue)
    ProducerTransactionValue.fromByteArray(producerTransaction.toByteArray) shouldBe producerTransaction
  }

  it should "be serialized/deserialized with negative ttl" in {
    val producerTransaction = ProducerTransactionValue(TransactionStates.Opened, -10, Long.MinValue, Long.MaxValue)
    ProducerTransactionValue.fromByteArray(producerTransaction.toByteArray) shouldBe producerTransaction
  }
}

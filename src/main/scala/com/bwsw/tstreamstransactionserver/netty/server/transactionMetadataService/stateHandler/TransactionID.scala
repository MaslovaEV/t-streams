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
package com.bwsw.tstreamstransactionserver.netty.server.transactionMetadataService.stateHandler

class TransactionID(val id: Long) extends AnyVal {
  def toByteArray: Array[Byte] = {
    val size = java.lang.Long.BYTES
    val buffer = java.nio.ByteBuffer.allocate(size)
      .putLong(id)
    buffer.flip()

    val bytes = new Array[Byte](size)
    buffer.get(bytes)
    bytes
  }

  override def toString: String = id.toString
}

object TransactionID {
  def apply(id: Long): TransactionID = new TransactionID(id)

  def fromByteArray(bytes: Array[Byte]): TransactionID = {
    val buffer = java.nio.ByteBuffer.wrap(bytes)
    val id = buffer.getLong
    TransactionID(id)
  }
}

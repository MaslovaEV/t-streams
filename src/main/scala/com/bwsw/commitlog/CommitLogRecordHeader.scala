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
package com.bwsw.commitlog

import java.nio.ByteBuffer

private[commitlog] case class CommitLogRecordHeader(id: Long, messageType: Byte, messageLength: Int, timestamp: Long)

object CommitLogRecordHeader{
  private[commitlog] def fromByteArray(binaryHeader: Array[Byte]) = {
    val buffer = ByteBuffer.wrap(binaryHeader)
    val id     = buffer.getLong()
    val messageType   = buffer.get()
    val messageLength = buffer.getInt()
    val timestamp     = buffer.getLong()
    CommitLogRecordHeader(id, messageType, messageLength, timestamp)
  }
}

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
package com.bwsw.tstreamstransactionserver.netty.server.handler.metadata

import com.bwsw.tstreamstransactionserver.netty.Protocol
import com.bwsw.tstreamstransactionserver.netty.server.TransactionServer
import com.bwsw.tstreamstransactionserver.netty.server.handler.RequestHandler
import com.bwsw.tstreamstransactionserver.rpc.{ServerException, TransactionService}
import GetTransactionIDByTimestampHandler.descriptor

import scala.concurrent.Future

private object GetTransactionIDByTimestampHandler {
  val descriptor = Protocol.GetTransactionIDByTimestamp
}

class GetTransactionIDByTimestampHandler(server: TransactionServer)
  extends RequestHandler {

  private def process(requestBody: Array[Byte]) = {
    val args = descriptor.decodeRequest(requestBody)
    server.getTransactionIDByTimestamp(args.timestamp)
  }

  override def handleAndGetResponse(requestBody: Array[Byte]): Future[Array[Byte]] = {
    Future.successful {
      val result = process(requestBody)
      descriptor.encodeResponse(
        TransactionService.GetTransactionIDByTimestamp.Result(Some(result))
      )
    }
  }

  override def handle(requestBody: Array[Byte]): Future[Unit] = {
    Future.failed(
      throw new UnsupportedOperationException(
        "It doesn't make any sense to get transaction ID by timestamp according to fire and forget policy"
      )
    )
  }

  override def createErrorResponse(message: String): Array[Byte] = {
    descriptor.encodeResponse(
      TransactionService.GetTransactionIDByTimestamp.Result(
        None,
        Some(ServerException(message)
        )
      )
    )
  }

  override def name: String = descriptor.name

  override def id: Byte = descriptor.methodID
}

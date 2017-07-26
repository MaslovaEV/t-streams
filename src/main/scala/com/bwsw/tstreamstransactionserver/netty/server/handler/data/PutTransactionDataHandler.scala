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
package com.bwsw.tstreamstransactionserver.netty.server.handler.data

import com.bwsw.tstreamstransactionserver.netty.Protocol
import com.bwsw.tstreamstransactionserver.netty.server.TransactionServer
import com.bwsw.tstreamstransactionserver.netty.server.handler.RequestHandler
import com.bwsw.tstreamstransactionserver.rpc.{ServerException, TransactionService}

import scala.concurrent.{ExecutionContext, Future}


private object PutTransactionDataHandler {
  val descriptor = Protocol.PutTransactionData
}

import PutTransactionDataHandler._

class PutTransactionDataHandler(server: TransactionServer,
                                context: ExecutionContext)
  extends RequestHandler {

  private def process(requestBody: Array[Byte]) = {
    val args = descriptor.decodeRequest(requestBody)
    server.putTransactionData(
      args.streamID,
      args.partition,
      args.transaction,
      args.data,
      args.from
    )
  }

  override def handleAndGetResponse(requestBody: Array[Byte]): Future[Array[Byte]] = {
    Future {
      val result = process(requestBody)
      descriptor.encodeResponse(
        TransactionService.PutTransactionData.Result(Some(result))
      )
    }(context)
  }

  override def handle(requestBody: Array[Byte]): Future[Unit] = {
    Future {
      process(requestBody)
      ()
    }(context)
  }

  override def createErrorResponse(message: String): Array[Byte] = {
    descriptor.encodeResponse(
      TransactionService.PutTransactionData.Result(
        None,
        Some(ServerException(message)
        )
      )
    )
  }

  override def name: String = descriptor.name

  override def id: Byte = descriptor.methodID
}

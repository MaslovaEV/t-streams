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

import com.bwsw.tstreamstransactionserver.netty.{Message, Protocol}
import com.bwsw.tstreamstransactionserver.netty.server.{OrderedExecutionContextPool, RecordType, TransactionServer}
import com.bwsw.tstreamstransactionserver.netty.server.commitLogService.ScheduledCommitLog
import com.bwsw.tstreamstransactionserver.netty.server.handler.SomeNameRequestProcessor
import com.bwsw.tstreamstransactionserver.rpc._
import PutSimpleTransactionAndDataProcessor.descriptor
import com.bwsw.tstreamstransactionserver.netty.server.authService.AuthService
import com.bwsw.tstreamstransactionserver.netty.server.subscriber.OpenTransactionStateNotifier
import com.bwsw.tstreamstransactionserver.netty.server.transportService.TransportService
import com.bwsw.tstreamstransactionserver.options.ServerOptions.AuthenticationOptions
import com.bwsw.tstreamstransactionserver.protocol.TransactionState
import com.bwsw.tstreamstransactionserver.rpc.TransactionService.PutSimpleTransactionAndData
import io.netty.channel.ChannelHandlerContext

import scala.concurrent.Future

private object PutSimpleTransactionAndDataProcessor {
  val descriptor = Protocol.PutSimpleTransactionAndData
}


class PutSimpleTransactionAndDataProcessor(server: TransactionServer,
                                           scheduledCommitLog: ScheduledCommitLog,
                                           notifier: OpenTransactionStateNotifier,
                                           authOptions: AuthenticationOptions,
                                           orderedExecutionPool: OrderedExecutionContextPool,
                                           authService: AuthService,
                                           transportService: TransportService)
  extends SomeNameRequestProcessor(
    authService,
    transportService) {

  override val name: String = descriptor.name

  override val id: Byte = descriptor.methodID

  private def process(txn: PutSimpleTransactionAndData.Args,
                      transactionID: Long) = {

    server.putTransactionData(
      txn.streamID,
      txn.partition,
      transactionID,
      txn.data,
      0
    )

    val transactions = collection.immutable.Seq(
      Transaction(Some(
        ProducerTransaction(
          txn.streamID,
          txn.partition,
          transactionID,
          TransactionStates.Opened,
          txn.data.size, 3000L
        )), None
      ),
      Transaction(Some(
        ProducerTransaction(
          txn.streamID,
          txn.partition,
          transactionID,
          TransactionStates.Checkpointed,
          txn.data.size,
          Long.MaxValue)), None
      )
    )

    val messageForPutTransactions =
      Protocol.PutTransactions.encodeRequest(
        TransactionService.PutTransactions.Args(transactions)
      )

    scheduledCommitLog.putData(
      RecordType.PutTransactionsType.id.toByte,
      messageForPutTransactions
    )
  }

  override protected def handle(message: Message,
                                ctx: ChannelHandlerContext): Unit = {
    val exceptionOpt = validate(message, ctx)
    if (exceptionOpt.isEmpty) {
      val args = descriptor.decodeRequest(message.body)
      val context = orderedExecutionPool.pool(args.streamID, args.partition)
      Future {
        val transactionID =
          server.getTransactionID

        process(args, transactionID)

        notifier.notifySubscribers(
          args.streamID,
          args.partition,
          transactionID,
          args.data.size,
          TransactionState.Status.Instant,
          Long.MaxValue,
          authOptions.key,
          isNotReliable = false
        )
      }(context)
    }
  }

  override protected def handleAndGetResponse(message: Message,
                                              ctx: ChannelHandlerContext): Unit = {
    val exceptionOpt = validate(message, ctx)
    if (exceptionOpt.isEmpty) {
      val args = descriptor.decodeRequest(message.body)
      val context = orderedExecutionPool.pool(args.streamID, args.partition)
      Future {
        val transactionID =
          server.getTransactionID

        process(args, transactionID)

        val response = descriptor.encodeResponse(
          TransactionService.PutSimpleTransactionAndData.Result(
            Some(transactionID)
          )
        )

        val responseMessage = message.copy(
          bodyLength = response.length,
          body = response
        )
        sendResponseToClient(responseMessage, ctx)

        notifier.notifySubscribers(
          args.streamID,
          args.partition,
          transactionID,
          args.data.size,
          TransactionState.Status.Instant,
          Long.MaxValue,
          authOptions.key,
          isNotReliable = false
        )
      }(context)
        .recover { case error =>
          logUnsuccessfulProcessing(name, error, message, ctx)
          val response = createErrorResponse(error.getMessage)
          val responseMessage = message.copy(
            bodyLength = response.length,
            body = response
          )
          sendResponseToClient(responseMessage, ctx)
        }(context)
    }
    else {
      val error = exceptionOpt.get
      logUnsuccessfulProcessing(name, error, message, ctx)
      val response = createErrorResponse(error.getMessage)
      val responseMessage = message.copy(
        bodyLength = response.length,
        body = response
      )
      sendResponseToClient(responseMessage, ctx)
    }
  }

  override def createErrorResponse(message: String): Array[Byte] = {
    descriptor.encodeResponse(
      TransactionService.PutSimpleTransactionAndData.Result(
        None,
        Some(ServerException(message)
        )
      )
    )
  }
}

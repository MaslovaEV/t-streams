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
package com.bwsw.tstreamstransactionserver.netty.server.handler

import com.bwsw.tstreamstransactionserver.configProperties.ServerExecutionContextGrids
import com.bwsw.tstreamstransactionserver.netty.server.authService.AuthService
import com.bwsw.tstreamstransactionserver.netty.server.commitLogService.ScheduledCommitLog
import com.bwsw.tstreamstransactionserver.netty.server.handler.auth.{AuthenticateProcessor, IsValidProcessor}
import com.bwsw.tstreamstransactionserver.netty.server.handler.consumer.{GetConsumerStateProcessor, PutConsumerCheckpointProcessor}
import com.bwsw.tstreamstransactionserver.netty.server.handler.data._
import com.bwsw.tstreamstransactionserver.netty.server.handler.metadata._
import com.bwsw.tstreamstransactionserver.netty.server.handler.stream.{CheckStreamExistsProcessor, DelStreamProcessor, GetStreamProcessor, PutStreamProcessor}
import com.bwsw.tstreamstransactionserver.netty.server.handler.test.{AuthValidatorHandler, ClientRequestHandler, DataPackageSizeValidatorHandler, RequestHandler}
import com.bwsw.tstreamstransactionserver.netty.server.handler.transport.{GetMaxPackagesSizesProcessor, GetZKCheckpointGroupServerPrefixProcessor}
import com.bwsw.tstreamstransactionserver.netty.server.subscriber.OpenTransactionStateNotifier
import com.bwsw.tstreamstransactionserver.netty.server.transportService.TransportService
import com.bwsw.tstreamstransactionserver.netty.server.{OrderedExecutionContextPool, TransactionServer}
import com.bwsw.tstreamstransactionserver.options.ServerOptions.{AuthenticationOptions, ServerRoleOptions, TransportOptions}

import scala.collection.Searching._
import scala.concurrent.ExecutionContext


final class RequestProcessorRouter(server: TransactionServer,
                                   scheduledCommitLog: ScheduledCommitLog,
                                   packageTransmissionOpts: TransportOptions,
                                   authOptions: AuthenticationOptions,
                                   orderedExecutionPool: OrderedExecutionContextPool,
                                   notifier: OpenTransactionStateNotifier,
                                   serverRoleOptions: ServerRoleOptions,
                                   executionContext:ServerExecutionContextGrids) {
  private val authService =
    new AuthService(authOptions)

  private val transportService =
    new TransportService(packageTransmissionOpts)

  private val serverWriteContext: ExecutionContext =
    executionContext.serverWriteContext
  private val serverReadContext: ExecutionContext =
    executionContext.serverReadContext
  private val commitLogContext =
    executionContext.commitLogContext

  private val handlers: Array[ClientRequestHandler] = Array(
    new GetCommitLogOffsetsProcessor(
      server,
      scheduledCommitLog,
      serverReadContext
    ),


    new PutStreamProcessor(
      server,
      serverReadContext
    ),
    new CheckStreamExistsProcessor(
      server,
      serverReadContext
    ),
    new GetStreamProcessor(
      server,
      serverReadContext
    ),
    new DelStreamProcessor(
      server,
      serverWriteContext
    ),


    new GetTransactionIDProcessor(
      server
    ),
    new GetTransactionIDByTimestampProcessor(
      server
    ),

    
    new PutTransactionProcessor(
      server,
      scheduledCommitLog,
      commitLogContext
    ),
    new PutTransactionsProcessor(
      server,
      scheduledCommitLog,
      commitLogContext
    ),
    new OpenTransactionProcessor(
      server,
      scheduledCommitLog,
      notifier,
      authOptions,
      orderedExecutionPool
    ),
    new GetTransactionProcessor(
      server,
      serverReadContext
    ),
    new GetLastCheckpointedTransactionProcessor(
      server,
      serverReadContext
    ),
    new ScanTransactionsProcessor(
      server,
      serverReadContext
    ),



    new PutProducerStateWithDataProcessor(
      server,
      scheduledCommitLog,
      commitLogContext
    ),
    new PutSimpleTransactionAndDataProcessor(
      server,
      scheduledCommitLog,
      notifier,
      authOptions,
      orderedExecutionPool
    ),
    new PutTransactionDataProcessor(
      server,
      serverWriteContext
    ),
    new GetTransactionDataProcessor(
      server,
      serverReadContext
    ),




    new PutConsumerCheckpointProcessor(
      server,
      scheduledCommitLog,
      commitLogContext
    ),
    new GetConsumerStateProcessor(
      server,
      serverReadContext
    ),



    new AuthenticateProcessor(
      authService
    ),
    new IsValidProcessor(
      authService
    ),



    new GetMaxPackagesSizesProcessor(
      packageTransmissionOpts
    ),
    new GetZKCheckpointGroupServerPrefixProcessor(
      serverRoleOptions
    )
  ).sorted


  private val handlersIDs = handlers.map(_.id)
  def handler(id: Byte): RequestProcessor =
    handlersIDs.search(id) match {
      case Found(index) => handlers(index)
      case _ =>
        throw new IllegalArgumentException(s"Not implemented method that has id: $id")
    }
}

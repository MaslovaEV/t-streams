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

package com.bwsw.tstreamstransactionserver.netty.server.multiNode.handler

import com.bwsw.tstreamstransactionserver.exception.Throwable.ServerIsSlaveException
import com.bwsw.tstreamstransactionserver.netty.RequestMessage
import com.bwsw.tstreamstransactionserver.netty.server.handler.ClientRequestHandler
import com.bwsw.tstreamstransactionserver.tracing.ServerTracer.tracer
import io.netty.channel.ChannelHandlerContext
import org.apache.bookkeeper.client.BKException

import scala.concurrent.{ExecutionContext, Future}

abstract class MultiNodePredefinedContextHandler(override final val id: Byte,
                                                 override final val name: String,
                                                 context: ExecutionContext)
  extends ClientRequestHandler(id, name) {

  override final def handle(message: RequestMessage,
                            ctx: ChannelHandlerContext,
                            acc: Option[Throwable]): Unit = {
    tracer.withTracing(message, name = getClass.getName + ".handle") {
      if (message.isFireAndForgetMethod)
        handleFireAndForgetRequest(message, ctx, acc)
      else
        handleRequest(message, ctx, acc)
    }
  }

  private def handleFireAndForgetRequest(message: RequestMessage,
                                         ctx: ChannelHandlerContext,
                                         acc: Option[Throwable]) = {
    if (acc.isEmpty) {
      Future(fireAndForget(message))(context)
    } else {
      logUnsuccessfulProcessing(
        name,
        acc.get,
        message,
        ctx
      )
    }
  }

  private def handleRequest(message: RequestMessage,
                            ctx: ChannelHandlerContext,
                            error: Option[Throwable]) = {
    if (error.isEmpty) {
      val futureResponse = getResponse(message)
      futureResponse
        .map { response =>
          sendResponse(message, response, ctx)
        }(context)
        .recover {
          case _: ServerIsSlaveException =>
          // do nothing
          case _: BKException =>
          // do nothing
          case throwable =>
            logUnsuccessfulProcessing(name, throwable, message, ctx)
            val response = createErrorResponse(throwable.getMessage)
            sendResponse(message, response, ctx)
        }(context)
    } else {
      val throwable = error.get
      logUnsuccessfulProcessing(name, throwable, message, ctx)
      val response = createErrorResponse(throwable.getMessage)
      sendResponse(message, response, ctx)
    }
  }

  protected def fireAndForget(message: RequestMessage): Unit

  protected def getResponse(message: RequestMessage): Future[Array[Byte]]
}

package com.bwsw.tstreamstransactionserver.netty.server.multiNode.handler.metadata

import com.bwsw.tstreamstransactionserver.netty.{RequestMessage, Protocol}
import com.bwsw.tstreamstransactionserver.netty.server.{RecordType, TransactionServer}
import com.bwsw.tstreamstransactionserver.netty.server.multiNode.RequestHandler
import com.bwsw.tstreamstransactionserver.rpc.{ServerException, TransactionService}
import io.netty.channel.ChannelHandlerContext
import org.apache.bookkeeper.client.{AsyncCallback, BKException, LedgerHandle}
import PutTransactionsHandler._
import com.bwsw.tstreamstransactionserver.netty.server.multiNode.bookkeperService.BookKeeperGateway
import com.bwsw.tstreamstransactionserver.netty.server.multiNode.bookkeperService.data.Record

private object PutTransactionsHandler {
  val protocol = Protocol.PutTransactions

  val isPuttedResponse: Array[Byte] = protocol.encodeResponse(
    TransactionService.PutTransactions.Result(Some(true))
  )
  val isNotPuttedResponse: Array[Byte] = protocol.encodeResponse(
    TransactionService.PutTransactions.Result(Some(false))
  )

  val fireAndForgetCallback = new AsyncCallback.AddCallback {
    override def addComplete(operationCode: Int,
                             ledgerHandle: LedgerHandle,
                             recordID: Long,
                             ctx: scala.Any): Unit = {}
  }
}

class PutTransactionsHandler(server: TransactionServer,
                             gateway: BookKeeperGateway)
  extends RequestHandler {

  private def process(requestBody: Array[Byte],
                      callback: AsyncCallback.AddCallback) = {
    gateway.doOperationWithCurrentWriteLedger { currentLedger =>

      val record = new Record(
        RecordType.PutTransactionsType,
        System.currentTimeMillis(),
        requestBody
      )

      currentLedger.asyncAddEntry(
        record.toByteArray,
        callback,
        null
      )
    }
  }

  override def getName: String = protocol.name

  override def handleAndSendResponse(requestBody: Array[Byte],
                                     message: RequestMessage,
                                     connection: ChannelHandlerContext): Unit = {

    val callback = new AsyncCallback.AddCallback {
      override def addComplete(operationCode: Int,
                               ledgerHandle: LedgerHandle,
                               recordID: Long,
                               ctx: scala.Any): Unit = {

        val messageResponse =
          if (BKException.Code.OK == operationCode) {
            message.copy(
              bodyLength = isPuttedResponse.length,
              body = isPuttedResponse
            )
          }
          else {
            message.copy(
              bodyLength = isNotPuttedResponse.length,
              body = isNotPuttedResponse
            )
          }
        connection.writeAndFlush(messageResponse.toByteArray)
      }
    }

    process(requestBody, callback)
  }

  override def handleFireAndForget(requestBody: Array[Byte]): Unit = {
    process(requestBody, fireAndForgetCallback)
  }

  override def createErrorResponse(message: String): Array[Byte] = {
    protocol.encodeResponse(
      TransactionService.PutTransactions.Result(
        None,
        Some(ServerException(message)
        )
      )
    )
  }
}

package netty.client

import java.util.concurrent.ConcurrentHashMap

import com.twitter.scrooge.ThriftStruct
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import netty.{Descriptors, Message}

import scala.annotation.tailrec
import scala.concurrent.{Promise => ScalaPromise}

class ClientHandler(private val reqIdToRep: ConcurrentHashMap[Int, ScalaPromise[ThriftStruct]]) extends SimpleChannelInboundHandler[Message] {


  override def channelRead0(ctx: ChannelHandlerContext, msg: Message): Unit = {
    import Descriptors._

    @tailrec
    def invokeMethod(message: Message): Unit = {
      val (method, messageSeqId) = Descriptor.decodeMethodName(message)
      scala.util.Try(reqIdToRep.get(messageSeqId).success(method match {
        case `putStreamMethod` =>
          Descriptors.PutStream.decodeResponse(message)

        case `doesStreamExistMethod` =>
          Descriptors.DoesStreamExist.decodeResponse(message)

        case `getStreamMethod` =>
          Descriptors.GetStream.decodeResponse(message)

        case `delStreamMethod` =>
          Descriptors.DelStream.decodeResponse(message)

        case `putTransactionMethod` =>
          Descriptors.PutTransaction.decodeResponse(message)

        case `putTranscationsMethod` =>
          Descriptors.PutTransactions.decodeResponse(message)

        case `scanTransactionsMethod` =>
          Descriptors.ScanTransactions.decodeResponse(message)

        case `putTransactionDataMethod` =>
          Descriptors.PutTransactionData.decodeResponse(message)

        case `getTransactionDataMethod` =>
          Descriptors.GetTransactionData.decodeResponse(message)

        case `setConsumerStateMethod` =>
          Descriptors.SetConsumerState.decodeResponse(message)

        case `getConsumerStateMethod` =>
          Descriptors.GetConsumerState.decodeResponse(message)

        case `authenticateMethod` =>
          Descriptors.Authenticate.decodeResponse(message)

        case `isValidMethod` =>
          Descriptors.IsValid.decodeResponse(message)
      })) match {
        case scala.util.Failure(error) =>
          Thread.sleep(5)
          invokeMethod(msg)
        case _=> ()
      }
    }
    invokeMethod(msg)
  }


  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }
}
/**
 * Generated by Scrooge
 *   version: 4.12.0
 *   rev: f7190e7f6b92684107b8cebf853d0d2403473022
 *   built at: 20161122-154730
 */
package transactionService.rpc

import com.twitter.finagle.SourcedException
import com.twitter.finagle.{service => ctfs}
import com.twitter.finagle.stats.{NullStatsReceiver, StatsReceiver}
import com.twitter.finagle.thrift.{Protocols, ThriftClientRequest}
import com.twitter.scrooge.{ThriftStruct, ThriftStructCodec}
import com.twitter.util.{Future, Return, Throw, Throwables}
import java.nio.ByteBuffer
import java.util.Arrays
import org.apache.thrift.protocol._
import org.apache.thrift.TApplicationException
import org.apache.thrift.transport.{TMemoryBuffer, TMemoryInputTransport}
import scala.collection.{Map, Set}
import scala.language.higherKinds


@javax.annotation.Generated(value = Array("com.twitter.scrooge.Compiler"))
class ConsumerService$FinagleClient(
    val service: com.twitter.finagle.Service[ThriftClientRequest, Array[Byte]],
    val protocolFactory: TProtocolFactory,
    val serviceName: String,
    stats: StatsReceiver,
    responseClassifier: ctfs.ResponseClassifier)
  extends ConsumerService[Future] {

  def this(
    service: com.twitter.finagle.Service[ThriftClientRequest, Array[Byte]],
    protocolFactory: TProtocolFactory = Protocols.binaryFactory(),
    serviceName: String = "ConsumerService",
    stats: StatsReceiver = NullStatsReceiver
  ) = this(
    service,
    protocolFactory,
    serviceName,
    stats,
    ctfs.ResponseClassifier.Default
  )

  import ConsumerService._

  protected def encodeRequest(name: String, args: ThriftStruct): ThriftClientRequest = {
    val buf = new TMemoryBuffer(512)
    val oprot = protocolFactory.getProtocol(buf)

    oprot.writeMessageBegin(new TMessage(name, TMessageType.CALL, 0))
    args.write(oprot)
    oprot.writeMessageEnd()

    val bytes = Arrays.copyOfRange(buf.getArray, 0, buf.length)
    new ThriftClientRequest(bytes, false)
  }

  protected def decodeResponse[T <: ThriftStruct](
    resBytes: Array[Byte],
    codec: ThriftStructCodec[T]
  ): T = {
    val iprot = protocolFactory.getProtocol(new TMemoryInputTransport(resBytes))
    val msg = iprot.readMessageBegin()
    try {
      if (msg.`type` == TMessageType.EXCEPTION) {
        val exception = TApplicationException.read(iprot) match {
          case sourced: SourcedException =>
            if (serviceName != "") sourced.serviceName = serviceName
            sourced
          case e => e
        }
        throw exception
      } else {
        codec.decode(iprot)
      }
    } finally {
      iprot.readMessageEnd()
    }
  }

  protected def missingResult(name: String) = {
    new TApplicationException(
      TApplicationException.MISSING_RESULT,
      name + " failed: unknown result"
    )
  }

  protected def setServiceName(ex: Throwable): Throwable =
    if (this.serviceName == "") ex
    else {
      ex match {
        case se: SourcedException =>
          se.serviceName = this.serviceName
          se
        case _ => ex
      }
    }

  // ----- end boilerplate.

  private[this] val scopedStats = if (serviceName != "") stats.scope(serviceName) else stats
  private[this] object __stats_setConsumerState {
    val RequestsCounter = scopedStats.scope("setConsumerState").counter("requests")
    val SuccessCounter = scopedStats.scope("setConsumerState").counter("success")
    val FailuresCounter = scopedStats.scope("setConsumerState").counter("failures")
    val FailuresScope = scopedStats.scope("setConsumerState").scope("failures")
  }
  
  def setConsumerState(token: Int, name: String, stream: String, partition: Int, transaction: Long): Future[Boolean] = {
    __stats_setConsumerState.RequestsCounter.incr()
    val inputArgs = SetConsumerState.Args(token, name, stream, partition, transaction)
    val replyDeserializer: Array[Byte] => _root_.com.twitter.util.Try[Boolean] =
      response => {
        val decodeResult: _root_.com.twitter.util.Try[SetConsumerState.Result] =
          _root_.com.twitter.util.Try {
            decodeResponse(response, SetConsumerState.Result)
          }
  
        decodeResult match {
          case t@_root_.com.twitter.util.Throw(_) =>
            t.cast[Boolean]
          case  _root_.com.twitter.util.Return(result) =>
            val serviceException: Throwable =
              if (false)
                null // can never happen, but needed to open a block
              else if (result.tokenInvalid.isDefined)
                setServiceName(result.tokenInvalid.get)
              else
                null
  
            if (result.success.isDefined)
              _root_.com.twitter.util.Return(result.success.get)
            else if (serviceException != null)
              _root_.com.twitter.util.Throw(serviceException)
            else
              _root_.com.twitter.util.Throw(missingResult("setConsumerState"))
        }
      }
  
    val serdeCtx = new _root_.com.twitter.finagle.thrift.DeserializeCtx[Boolean](inputArgs, replyDeserializer)
    _root_.com.twitter.finagle.context.Contexts.local.let(
      _root_.com.twitter.finagle.thrift.DeserializeCtx.Key,
      serdeCtx
    ) {
      val serialized = encodeRequest("setConsumerState", inputArgs)
      this.service(serialized).flatMap { response =>
        Future.const(serdeCtx.deserialize(response))
      }.respond { response =>
        val responseClass = responseClassifier.applyOrElse(
          ctfs.ReqRep(inputArgs, response),
          ctfs.ResponseClassifier.Default)
        responseClass match {
          case ctfs.ResponseClass.Successful(_) =>
            __stats_setConsumerState.SuccessCounter.incr()
          case ctfs.ResponseClass.Failed(_) =>
            __stats_setConsumerState.FailuresCounter.incr()
            response match {
              case Throw(ex) =>
                setServiceName(ex)
                __stats_setConsumerState.FailuresScope.counter(Throwables.mkString(ex): _*).incr()
              case _ =>
            }
        }
      }
    }
  }
  private[this] object __stats_getConsumerState {
    val RequestsCounter = scopedStats.scope("getConsumerState").counter("requests")
    val SuccessCounter = scopedStats.scope("getConsumerState").counter("success")
    val FailuresCounter = scopedStats.scope("getConsumerState").counter("failures")
    val FailuresScope = scopedStats.scope("getConsumerState").scope("failures")
  }
  
  def getConsumerState(token: Int, name: String, stream: String, partition: Int): Future[Long] = {
    __stats_getConsumerState.RequestsCounter.incr()
    val inputArgs = GetConsumerState.Args(token, name, stream, partition)
    val replyDeserializer: Array[Byte] => _root_.com.twitter.util.Try[Long] =
      response => {
        val decodeResult: _root_.com.twitter.util.Try[GetConsumerState.Result] =
          _root_.com.twitter.util.Try {
            decodeResponse(response, GetConsumerState.Result)
          }
  
        decodeResult match {
          case t@_root_.com.twitter.util.Throw(_) =>
            t.cast[Long]
          case  _root_.com.twitter.util.Return(result) =>
            val serviceException: Throwable =
              if (false)
                null // can never happen, but needed to open a block
              else if (result.tokenInvalid.isDefined)
                setServiceName(result.tokenInvalid.get)
              else
                null
  
            if (result.success.isDefined)
              _root_.com.twitter.util.Return(result.success.get)
            else if (serviceException != null)
              _root_.com.twitter.util.Throw(serviceException)
            else
              _root_.com.twitter.util.Throw(missingResult("getConsumerState"))
        }
      }
  
    val serdeCtx = new _root_.com.twitter.finagle.thrift.DeserializeCtx[Long](inputArgs, replyDeserializer)
    _root_.com.twitter.finagle.context.Contexts.local.let(
      _root_.com.twitter.finagle.thrift.DeserializeCtx.Key,
      serdeCtx
    ) {
      val serialized = encodeRequest("getConsumerState", inputArgs)
      this.service(serialized).flatMap { response =>
        Future.const(serdeCtx.deserialize(response))
      }.respond { response =>
        val responseClass = responseClassifier.applyOrElse(
          ctfs.ReqRep(inputArgs, response),
          ctfs.ResponseClassifier.Default)
        responseClass match {
          case ctfs.ResponseClass.Successful(_) =>
            __stats_getConsumerState.SuccessCounter.incr()
          case ctfs.ResponseClass.Failed(_) =>
            __stats_getConsumerState.FailuresCounter.incr()
            response match {
              case Throw(ex) =>
                setServiceName(ex)
                __stats_getConsumerState.FailuresScope.counter(Throwables.mkString(ex): _*).incr()
              case _ =>
            }
        }
      }
    }
  }
}

package com.bwsw.tstreams.agents.consumer.subscriber

import com.bwsw.tstreams.common.ProtocolMessageSerializer
import com.bwsw.tstreams.common.ProtocolMessageSerializer.ProtocolMessageSerializerException
import com.bwsw.tstreams.proto.protocol.TransactionState
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.util.CharsetUtil

import scala.collection.mutable

/**
  * Created by Ivan Kudryavtsev on 25.08.16.
  * Handler for netty which handles updates from producers.
  */
@ChannelHandler.Sharable
class TransactionStateMessageChannelHandler(transactionsBufferWorkers: mutable.Map[Int, TransactionBufferWorker]) extends SimpleChannelInboundHandler[DatagramPacket] {

  private val partitionCache = mutable.Map[Int, TransactionBufferWorker]()

  transactionsBufferWorkers
    .foreach(id_w => id_w._2.getPartitions().foreach(p => partitionCache(p) = id_w._2))

  override def channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket): Unit = {
    try {
      val bytes = new Array[Byte](msg.content().readableBytes())
      val buf = msg.content().readBytes(bytes)
      val m = TransactionState.parseFrom(bytes)
      if (partitionCache.contains(m.partition))
        partitionCache(m.partition).update(m)
      else
        Subscriber.logger.warn(s"Unknown partition ${m.partition} found in Message: $msg.")
    } catch {
      case e: ProtocolMessageSerializerException =>
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext) = {
    ctx.flush()
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
    Subscriber.logger.warn(cause.getMessage)
  }
}


package com.bwsw.tstreamstransactionserver.netty.server.multiNode

import com.bwsw.tstreamstransactionserver.netty.server.handler.RequestRouter
import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import org.slf4j.Logger

class ServerHandler(requestHandlerChooser: RequestRouter, logger: Logger)
  extends SimpleChannelInboundHandler[ByteBuf] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf): Unit = ???
}

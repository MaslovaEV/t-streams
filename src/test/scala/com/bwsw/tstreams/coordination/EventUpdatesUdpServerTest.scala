package com.bwsw.tstreams.coordination

import java.net.{DatagramSocket, InetAddress}
import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.bwsw.tstreams.coordination.server.EventUpdatesUdpServer
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.util.CharsetUtil
import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by ivan on 11.04.17.
  */
class EventUpdatesUdpServerTest extends FlatSpec with Matchers {
  it should "deliver messages correctly" in {
    val l = new CountDownLatch(1)
    var m = ""
    val srv = new EventUpdatesUdpServer("127.0.0.1", 8123, new SimpleChannelInboundHandler[DatagramPacket] {
      override def channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket): Unit = {
        m = msg.content().toString(CharsetUtil.UTF_8)
        l.countDown()
      }
    })
    srv.start()
    val clientSocket = new DatagramSocket()
    val host = InetAddress.getByName("127.0.0.1")
    val port = 8123
    val bytes = "test".getBytes()
    val sendPacket = new java.net.DatagramPacket(bytes, bytes.length, host, port)
    clientSocket.send(sendPacket)
    l.await(1, TimeUnit.SECONDS)
    srv.stop()
    m shouldBe "test"
  }
}
package it.client

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import com.bwsw.tstreamstransactionserver.netty.SocketHostPortPair
import com.bwsw.tstreamstransactionserver.netty.client.NettyConnectionHandler
import com.bwsw.tstreamstransactionserver.netty.server.ServerInitializer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.epoll.{EpollEventLoopGroup, EpollServerSocketChannel}
import io.netty.channel.socket.SocketChannel
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import org.scalatest.{FlatSpec, Matchers}
import util.Utils

class NettyConnectionHandlerTest
  extends FlatSpec
    with Matchers {

  private def handler = new SimpleChannelInboundHandler[Nothing] {
    override def channelRead0(ctx: ChannelHandlerContext, msg: Nothing): Unit = {}
  }

  private def handlersChain = new ChannelInitializer[SocketChannel] {
    override def initChannel(ch: SocketChannel): Unit = {
      ch.pipeline()
        .addLast(handler)
    }
  }

  private def getClient(workerGroup: EventLoopGroup,
                        socket: SocketHostPortPair,
                        onConnectionLostDo: => Unit) = {
    new NettyConnectionHandler(
      workerGroup,
      handlersChain,
      3000,
      socket,
      onConnectionLostDo
    )
  }

  private def startServer(socket: SocketHostPortPair): (EpollEventLoopGroup, EpollEventLoopGroup) = {
    val latch = new CountDownLatch(1)

    val workerGroup = new EpollEventLoopGroup()
    val bossGroup = new EpollEventLoopGroup(1)

    val serverStartupTask = new Runnable {
      override def run(): Unit = {
        val b = new ServerBootstrap()
        b.group(bossGroup, workerGroup)
          .channel(classOf[EpollServerSocketChannel])
          .handler(new LoggingHandler(LogLevel.DEBUG))
          .childHandler(handlersChain)
          .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128)
          .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, false)

        b.bind(socket.address, socket.port).sync()
        latch.countDown()
      }
    }

    new Thread(serverStartupTask).start()
    val isStarted = latch.await(3000, TimeUnit.MILLISECONDS)
    if (!isStarted)
      throw new Exception("Server isn't started!")
    else {
      (bossGroup, workerGroup)
    }
  }

  it should "reconnect to server multiple times" in {
    val host = "127.0.0.1"
    val port = Utils.getRandomPort
    val socket = SocketHostPortPair(
      host,
      port
    )

    val reconnectAttemptsNumber = 10
    val timePerReconnect = 100

    val (bossGroup, eventLoopGroup) = startServer(socket)

    val latch = new CountDownLatch(reconnectAttemptsNumber)

    val workerGroup: EventLoopGroup = new EpollEventLoopGroup()

    getClient(workerGroup, socket, {
      latch.countDown()
    })

    bossGroup.shutdownGracefully().getNow
    eventLoopGroup.shutdownGracefully().getNow

    latch.await(
      reconnectAttemptsNumber*timePerReconnect,
      TimeUnit.MILLISECONDS
    ) shouldBe true
  }

}

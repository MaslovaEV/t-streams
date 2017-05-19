package com.bwsw.tstreams.common

import java.net.{DatagramPacket, DatagramSocket, SocketException}
import java.util.concurrent.CountDownLatch

import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object UdpProcessor {
  var BUFFER_SIZE = 508
  var BIND_TRIES = 10
  var BIND_FAIL_RETRY_DELAY = 500
}

abstract class UdpProcessor {
  val socket = socketInitializer()

  val logger = LoggerFactory.getLogger(this.getClass)

  def socketInitializer() = new DatagramSocket()

  def bind()

  def bootstrapOperation(): Unit = {
    for(trial <- (0 until UdpProcessor.BIND_TRIES + 1)) {
      Try(bind()) match {
        case Success(_) => return
        case Failure(exception) =>
          if(trial == UdpProcessor.BIND_TRIES)
            throw exception

          logger.warn(s"Socket bind error. Exception is: $exception, this is $trial try, " +
            s" total ${UdpProcessor.BIND_TRIES} tries. Next is after: ${UdpProcessor.BIND_FAIL_RETRY_DELAY} ms.")
      }
    }
  }

  def handleMessage(socket: DatagramSocket, packet: DatagramPacket): Unit

  def operationLoop() = {
    try {
      while (true) {
        val packet = new DatagramPacket(new Array[Byte](UdpProcessor.BUFFER_SIZE), UdpProcessor.BUFFER_SIZE)
        socket.receive(packet)
        handleMessage(socket, packet)
      }
    } catch {
      case ex: SocketException => if(!socket.isClosed) throw ex
    }
  }

  val startLatch = new CountDownLatch(1)
  val ioBlockingThread = new Thread(() => {
    try {
      startLatch.countDown()
      operationLoop()
    } catch {
      case ex: InterruptedException =>
        logger.info("IO Thread was interrupted.")
      case ex: Exception => throw ex
    }
  })

  def start() = {
    bootstrapOperation()
    ioBlockingThread.start()
    startLatch.await()
    this
  }

  def stop() = {
    socket.close()
    ioBlockingThread.join()
  }
}

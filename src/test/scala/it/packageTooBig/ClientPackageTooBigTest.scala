package it.packageTooBig

import java.io.File
import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.bwsw.tstreamstransactionserver.exception.Throwable.PackageTooBigException
import com.bwsw.tstreamstransactionserver.netty.client.InetClientProxy
import com.bwsw.tstreamstransactionserver.netty.server.SingleNodeServer
import com.bwsw.tstreamstransactionserver.options.ClientOptions.{AuthOptions, ConnectionOptions}
import com.bwsw.tstreamstransactionserver.options.CommonOptions.ZookeeperOptions
import com.bwsw.tstreamstransactionserver.options.ServerOptions.{BootstrapOptions, StorageOptions, TransportOptions}
import com.bwsw.tstreamstransactionserver.options.SingleNodeServerBuilder
import org.apache.commons.io.FileUtils
import org.apache.curator.test.TestingServer
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ClientPackageTooBigTest
  extends FlatSpec
    with Matchers {

   private val serverStorageOptions = StorageOptions(path = "/tmp")
   private val packageTransmissionOptions = TransportOptions(maxMetadataPackageSize = 1000000)

   private def startTransactionServer(zkConnectionString: String): SingleNodeServer = {
     val server = new SingleNodeServerBuilder()
       .withServerStorageOptions(serverStorageOptions)
       .withZookeeperOptions(ZookeeperOptions(endpoints = zkConnectionString))
       .withPackageTransmissionOptions(packageTransmissionOptions)
       .withBootstrapOptions(BootstrapOptions())
       .build()

     val latch = new CountDownLatch(1)
     new Thread(() => {
       server.start(latch.countDown())
     }).start()

     latch.await()
     server
   }

  "Client" should "not allow to transmit amount of data that is greater than maxMetadataPackageSize or maxDataPackageSize (throw PackageTooBigException)" in {
    FileUtils.deleteDirectory(new File(serverStorageOptions.path + java.io.File.separatorChar + serverStorageOptions.metadataDirectory))
    FileUtils.deleteDirectory(new File(serverStorageOptions.path + java.io.File.separatorChar + serverStorageOptions.dataDirectory))
    FileUtils.deleteDirectory(new File(serverStorageOptions.path + java.io.File.separatorChar + serverStorageOptions.commitLogRocksDirectory))
    FileUtils.deleteDirectory(new File(serverStorageOptions.path + java.io.File.separatorChar + serverStorageOptions.commitLogRawDirectory))

    val zkTestServer = new TestingServer(true)

    val server = startTransactionServer(zkTestServer.getConnectString)

    val client = new InetClientProxy(
      ConnectionOptions(),
      AuthOptions(),
      ZookeeperOptions(endpoints = zkTestServer.getConnectString)
    )

    assertThrows[PackageTooBigException] {
      Await.result(client.putStream(
        "Too big message",
        1,
        Some(new String(new Array[Byte](packageTransmissionOptions.maxMetadataPackageSize))), 1
      ), Duration(5, TimeUnit.SECONDS))
    }

    client.shutdown()
    zkTestServer.close()
    server.shutdown()
  }
}

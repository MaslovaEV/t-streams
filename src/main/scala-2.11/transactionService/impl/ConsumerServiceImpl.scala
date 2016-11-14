package transactionService.impl

import java.io.{Closeable, File}
import java.nio.file.{Files, Paths}

import scala.concurrent.{Future => ScalaFuture}
import com.twitter.util.{Future => TwitterFuture}
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.twitter_util.UtilBijections._

import com.sleepycat.je.{DatabaseEntry, Environment, EnvironmentConfig}
import com.sleepycat.persist.{EntityStore, StoreConfig}
import com.sleepycat.persist.model._

import transactionService.rpc.ConsumerService
import transactionService.impl.`implicit`.Implicits._
import ConsumerServiceImpl._

trait ConsumerServiceImpl extends ConsumerService[TwitterFuture] with Closeable {
  def getConsumerState(token: String, name: String, stream: String, partition: Int): TwitterFuture[Long] =  {
    implicit val context = transactionService.impl.thread.Context.transactionContexts.getContext(partition, stream.toInt)
    val transaction = ScalaFuture {
      val pIdx = entityStore.getPrimaryIndex(classOf[ConsumerKey], classOf[ConsumerServiceImpl.Consumer])
      Option(pIdx.get(new ConsumerKey(name, stream, partition))) match {
        case Some(consumer) => consumer.transactionId
        case None => -1L
      }
    }.as[TwitterFuture[Long]]

    transaction flatMap TwitterFuture.value
  }

  def setConsumerState(token: String, name: String, stream: String, partition: Int, transaction: Long): TwitterFuture[Boolean] =  {
    implicit val context = transactionService.impl.thread.Context.transactionContexts.getContext(partition, stream.toInt)
    val isSetted = ScalaFuture {
      val pIdx = entityStore.getPrimaryIndex(classOf[ConsumerKey], classOf[ConsumerServiceImpl.Consumer])
      pIdx.putNoOverwrite(new ConsumerServiceImpl.Consumer(name, stream, partition, transaction))
    }.as[TwitterFuture[Boolean]]

    isSetted flatMap TwitterFuture.value
  }

  override def close(): Unit = {
    entityStore.close()
    environment.close()
  }
}

private object ConsumerServiceImpl {
  final val pathToDatabases = "/tmp"
  final val storeName = "ConsumerStore"

  final val consumerKey = new DatabaseEntry("consumer")
  final val streamKey = new DatabaseEntry("stream")
  final val partitionKey = new DatabaseEntry("partition")
  final val txnKey = new DatabaseEntry("txn")

  val directory = StreamServiceImpl.createDirectory("consumer")
  val environmentConfig = new EnvironmentConfig()
    .setAllowCreate(true)
  val storeConfig = new StoreConfig()
    .setAllowCreate(true)
  val environment = new Environment(directory, environmentConfig)
  val entityStore = new EntityStore(environment, storeName, storeConfig)


  def createDirectory(name: String = pathToDatabases, deleteAtExit: Boolean = true): File = {
    val path = {
      val dir = Paths.get(name)
      if (Files.exists(dir)) dir else java.nio.file.Files.createDirectory(Paths.get(name))
    }

    import org.apache.commons.io.FileUtils

    if (deleteAtExit)
      Runtime.getRuntime.addShutdownHook(new Thread {
        override def run() {
          FileUtils.forceDelete(path.toFile)
        }
      })
    path.toFile
  }

  @Entity
  class Consumer {
    @PrimaryKey private var key: ConsumerKey = _
    private var transactionIDDB: java.lang.Long = _

    def this(name: String,
             stream: String,
             partition: Int,
             transactionID: java.lang.Long
            ) {
      this()
      this.transactionIDDB = transactionID
      this.key = new ConsumerKey(name, stream, partition)
    }

    def transactionId: Long = transactionIDDB
    def name: String = key.name
    def stream: String = key.stream
    def partition: Int = key.partition
  }

  @Persistent
  class ConsumerKey {
    @KeyField(1) var name: String = _
    @KeyField(2) var stream: String = _
    @KeyField(3) var partition: Int = _
    def this(name: String, stream: String, partition:Int) = {
      this()
      this.name = name
      this.stream = stream
      this.partition = partition
    }

    override def toString: String = s"consumer:$name\tstream:$stream\tpartition:$partition\t"
  }
}

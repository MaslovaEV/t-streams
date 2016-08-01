package agents.subscriber

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

import com.bwsw.tstreams.agents.consumer.Offsets.Oldest
import com.bwsw.tstreams.agents.consumer.subscriber.{BasicSubscriberCallback, BasicSubscribingConsumer}
import com.bwsw.tstreams.agents.consumer.{BasicConsumerOptions, SubscriberCoordinationOptions}
import com.bwsw.tstreams.agents.producer.InsertionType.BatchInsert
import com.bwsw.tstreams.agents.producer.{BasicProducer, BasicProducerOptions, ProducerCoordinationOptions, ProducerPolicies}
import com.bwsw.tstreams.coordination.transactions.transport.impl.TcpTransport
import com.bwsw.tstreams.env.TSF_Dictionary
import com.bwsw.tstreams.streams.BasicStream
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import testutils._

import scala.collection.mutable.ListBuffer


class AManyBasicProducersStreamingInManyPartitionsAndSubscriberTest extends FlatSpec with Matchers with BeforeAndAfterAll with TestUtils {
  val timeoutForWaiting = 60
  val totalPartitions = 4
  val totalTxn = 10
  val totalElementsInTxn = 3
  val producersAmount = 10
  val dataToSend = (for (part <- 0 until totalElementsInTxn) yield randomString).sorted
  val lock = new ReentrantLock()
  val map = scala.collection.mutable.Map[Int, ListBuffer[UUID]]()
  (0 until totalPartitions) foreach { partition =>
    map(partition) = ListBuffer.empty[UUID]
  }
  var cnt = 0

  val callback = new BasicSubscriberCallback[String] {
    override def onEvent(subscriber: BasicSubscribingConsumer[String], partition: Int, transactionUuid: UUID): Unit = {
      lock.lock()
      map(partition) += transactionUuid
      cnt += 1
      lock.unlock()
    }

  }


  f.setProperty(TSF_Dictionary.Stream.name,"test_stream").
    setProperty(TSF_Dictionary.Stream.partitions, 4).
    setProperty(TSF_Dictionary.Stream.ttl, 60 * 10).
    setProperty(TSF_Dictionary.Coordination.connection_timeout, 7).
    setProperty(TSF_Dictionary.Coordination.ttl, 7).
    setProperty(TSF_Dictionary.Producer.master_timeout, 5).
    setProperty(TSF_Dictionary.Producer.Transaction.ttl, 3).
    setProperty(TSF_Dictionary.Producer.Transaction.keep_alive, 1).
    setProperty(TSF_Dictionary.Consumer.transaction_preload, 10).
    setProperty(TSF_Dictionary.Consumer.data_preload, 10)

  val subscriber = f.getSubscriber[String](
    name = "test_subscriber",
    txnGenerator = LocalGeneratorCreator.getGen(),
    converter = arrayByteToStringConverter,
    partitions = (0 until totalPartitions).toList,
    callback = callback,
    offset = Oldest,
    isUseLastOffset = true)

  "Some amount of producers and subscriber" should "producers - send transactions in many partition" +
    " (each producer send each txn in only one partition without intersection " +
    " for ex. producer1 in partition1, producer2 in partition2, producer3 in partition3 etc...)," +
    " subscriber - retrieve them all(with callback) in sorted order" in {

    val producers: List[BasicProducer[String]] =
      (0 until producersAmount)
        .toList
        .map(x => getProducer(List(x % totalPartitions), totalPartitions))

    val producersThreads = producers.map(p =>
      new Thread(new Runnable {
        def run() {
          var i = 0
          while (i < totalTxn) {
            Thread.sleep(2000)
            val txn = p.newTransaction(ProducerPolicies.errorIfOpened)
            dataToSend.foreach(x => txn.send(x))
            txn.checkpoint()
            i += 1
          }
        }
      }))


    subscriber.start()
    producersThreads.foreach(x => x.start())
    producersThreads.foreach(x => x.join(timeoutForWaiting * 1000L))
    Thread.sleep(20 * 1000)

    producers.foreach(_.stop())
    subscriber.stop()

    assert(map.values.map(x => x.size).sum == totalTxn * producersAmount)
    map foreach { case (_, list) =>
      list.map(x => (x, x.timestamp())).sortBy(_._2).map(x => x._1) shouldEqual list
    }
  }

  def getProducer(usedPartitions: List[Int], totalPartitions: Int): BasicProducer[String] = {
    val port = TestUtils.getPort
    f.setProperty(TSF_Dictionary.Producer.master_bind_port, port)
    f.getProducer[String](
      name = "test_producer",
      txnGenerator = LocalGeneratorCreator.getGen(),
      converter = stringToArrayByteConverter,
      partitions = (0 until totalPartitions).toList,
      isLowPriority = false)
  }

  override def afterAll(): Unit = {
    onAfterAll()
  }
}
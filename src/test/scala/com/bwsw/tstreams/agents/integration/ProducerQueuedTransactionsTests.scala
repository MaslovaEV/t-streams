package com.bwsw.tstreams.agents.integration

import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.bwsw.tstreams.agents.consumer.Offset.Newest
import com.bwsw.tstreams.agents.consumer.{ConsumerTransaction, TransactionOperator}
import com.bwsw.tstreams.agents.producer.NewProducerTransactionPolicy
import com.bwsw.tstreams.env.ConfigurationOptions
import com.bwsw.tstreams.env.defaults.TStreamsFactoryProducerDefaults
import com.bwsw.tstreams.testutils.{TestStorageServer, TestUtils}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.collection.mutable.ListBuffer

/**
  * Created by Ivan Kudryavtsev on 16.05.17.
  */
class ProducerQueuedTransactionsTests extends FlatSpec with Matchers with BeforeAndAfterAll with TestUtils {
  lazy val srv = TestStorageServer.getNewClean()

  override def beforeAll(): Unit = {
    srv
    createNewStream()
  }

  override def afterAll(): Unit = {
    TestStorageServer.dispose(srv)
    onAfterAll()
  }

  it should "create queued transactions, write them and subscriber must be able to read them" in {
    val TOTAL = 1000
    val latch = new CountDownLatch(TOTAL)

    val producer = f.getProducer(
      name = "test_producer",
      partitions = Set(0))

    val s = f.getSubscriber(name = "subscriber",
      partitions = Set(0),
      offset = Newest,
      useLastOffset = false,
      callback = (consumer: TransactionOperator, transaction: ConsumerTransaction) => latch.countDown())
    s.start()

    for (it <- 0 until TOTAL) {
      val transaction = producer.newTransaction(NewProducerTransactionPolicy.EnqueueIfOpened)
      transaction.send("test")
    }

    producer.checkpoint()
    producer.stop()

    latch.await(60, TimeUnit.SECONDS) shouldBe true

    s.stop()
  }

  it should "create queued transactions, keep them alive, write them and subscriber must be able to read them" in {
    val TOTAL = 1000
    val latch = new CountDownLatch(TOTAL)

    val producer = f.getProducer(
      name = "test_producer",
      partitions = Set(0))

    val subscriber = f.getSubscriber(name = "subscriber",
      partitions = Set(0),
      offset = Newest,
      useLastOffset = false,
      callback = (consumer: TransactionOperator, transaction: ConsumerTransaction) => latch.countDown())
    subscriber.start()

    for (it <- 0 until TOTAL) {
      val transaction = producer.newTransaction(NewProducerTransactionPolicy.EnqueueIfOpened)
      transaction.send("test")
    }

    Thread.sleep(f.getProperty(ConfigurationOptions.Producer.Transaction.ttlMs).asInstanceOf[Int] + 1000)

    producer.checkpoint()
    producer.stop()

    latch.await(60, TimeUnit.SECONDS) shouldBe true

    subscriber.stop()
  }

  it should "create queued transactions, cancel them, create and get correctly with big TTL" in {
    val TOTAL = 1000
    val latch = new CountDownLatch(TOTAL)
    val producerAcc = ListBuffer[Long]()
    val subscriberAcc = ListBuffer[Long]()
    val nf = f.copy()
    nf.setProperty(ConfigurationOptions.Producer.Transaction.ttlMs, TStreamsFactoryProducerDefaults.Producer.Transaction.ttlMs.max)

    val producer = nf.getProducer(
      name = "test_producer",
      partitions = Set(0))

    val s = nf.getSubscriber(name = "subscriber",
      partitions = Set(0),
      offset = Newest,
      useLastOffset = false,
      callback = (consumer: TransactionOperator, transaction: ConsumerTransaction) => this.synchronized {
        subscriberAcc.append(transaction.getTransactionID)
        latch.countDown()
      })
    s.start()

    for (it <- 0 until TOTAL) {
      val transaction = producer.newTransaction(NewProducerTransactionPolicy.EnqueueIfOpened)
      transaction.send("test")
    }

    producer.cancel()

    for (it <- 0 until TOTAL) {
      val transaction = producer.newTransaction(NewProducerTransactionPolicy.EnqueueIfOpened)
      transaction.send("test")
      producerAcc.append(transaction.getTransactionID)
    }
    producer.checkpoint()

    producer.stop()

    latch.await(60, TimeUnit.SECONDS) shouldBe true
    producerAcc shouldBe subscriberAcc

    s.stop()
  }
}

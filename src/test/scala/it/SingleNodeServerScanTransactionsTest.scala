package it

import java.util.concurrent.atomic.AtomicLong

import com.bwsw.tstreamstransactionserver.netty.server.transactionMetadataService.ProducerTransactionRecord
import com.bwsw.tstreamstransactionserver.rpc.{ProducerTransaction, TransactionStates}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import util.Utils._


class SingleNodeServerScanTransactionsTest
  extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {

  private val rand = scala.util.Random

  private def getRandomStream = com.bwsw.tstreamstransactionserver.rpc.StreamValue(
    name = rand.nextInt(10000).toString,
    partitions = rand.nextInt(10000),
    description = if (rand.nextBoolean()) Some(rand.nextInt(10000).toString) else None,
    ttl = Long.MaxValue
  )

  private def getRandomProducerTransaction(streamID: Int, streamObj: com.bwsw.tstreamstransactionserver.rpc.StreamValue, txnID: Long, ttlTxn: Long) = ProducerTransaction(
    stream = streamID,
    partition = streamObj.partitions,
    transactionID = txnID,
    state = TransactionStates.Opened,
    quantity = -1,
    ttl = ttlTxn
  )

  private lazy val (zkServer, zkClient) =
    startZkServerAndGetIt

  override def beforeAll(): Unit = {
    zkServer
    zkClient
  }

  override def afterAll(): Unit = {
    zkClient.close()
    zkServer.close()
  }


  it should "correctly return producerTransactions on: LT < A: " +
    "return (LT, Nil), where A - from transaction bound, B - to transaction bound" in {

    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>
      val streamsNumber = 5

      val streams = Array.fill(streamsNumber)(getRandomStream)
      val streamsAndIDs = streams.map(stream =>
        (transactionServer.putStream(stream.name, stream.partitions, stream.description, stream.ttl), stream)
      )

      streamsAndIDs foreach { case (streamID, stream) =>
        val currentTimeInc = new AtomicLong(System.currentTimeMillis())
        val transactionRootChain = getRandomProducerTransaction(streamID, stream, 1, Long.MaxValue)
        val producerTransactionsWithTimestamp: Array[(ProducerTransaction, Long)] =
          Array(
            (transactionRootChain, currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 1L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Invalid), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 4L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement())
          )

        val transactionsWithTimestamp =
          producerTransactionsWithTimestamp.map { case (producerTxn, timestamp) =>
            ProducerTransactionRecord(producerTxn, timestamp)
          }

        val bigCommit = transactionServer.getBigCommit(1L)
        bigCommit.putProducerTransactions(transactionsWithTimestamp)
        bigCommit.commit()

        val result = transactionServer.scanTransactions(
          streamID,
          stream.partitions,
          2L,
          4L,
          Int.MaxValue,
          Set(TransactionStates.Opened)
        )

        result.producerTransactions shouldBe empty
        result.lastOpenedTransactionID shouldBe 3L
      }
    }
  }

  it should "correctly return producerTransactions on: LT < A: " +
    "return (LT, Nil), where A - from transaction bound, B - to transaction bound. " +
    "No transactions had been persisted on server before scanTransactions was called" in {

    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>
      val streamsNumber = 5

      val streams = Array.fill(streamsNumber)(getRandomStream)
      val streamsAndIDs = streams.map(stream =>
        (transactionServer.putStream(stream.name, stream.partitions, stream.description, stream.ttl), stream)
      )

      streamsAndIDs foreach { case (streamID, stream) =>
        val result = transactionServer.scanTransactions(streamID, stream.partitions, 2L, 4L, Int.MaxValue, Set(TransactionStates.Opened))

        result.producerTransactions shouldBe empty
        result.lastOpenedTransactionID shouldBe -1L
      }
    }
  }

  it should "correctly return producerTransactions on: A <= LT < B: " +
    "return (LT, AvailableTransactions[A, LT]), where A - from transaction bound, B - to transaction bound" in {
    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>
      val streamsNumber = 5

      val streams = Array.fill(streamsNumber)(getRandomStream)
      val streamsAndIDs = streams.map(stream =>
        (transactionServer.putStream(stream.name, stream.partitions, stream.description, stream.ttl), stream)
      )


      streamsAndIDs foreach { case (streamID, stream) =>
        val currentTimeInc = new AtomicLong(System.currentTimeMillis())
        val transactionRootChain = getRandomProducerTransaction(streamID, stream, 1, Long.MaxValue)
        val producerTransactionsWithTimestamp: Array[(ProducerTransaction, Long)] =
          Array(
            (transactionRootChain, currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 1L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 4L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement())
          )

        val transactionsWithTimestamp = producerTransactionsWithTimestamp.map {
          case (producerTxn, timestamp) => ProducerTransactionRecord(producerTxn, timestamp)
        }

        val bigCommit = transactionServer.getBigCommit(1L)
        bigCommit.putProducerTransactions(transactionsWithTimestamp)
        bigCommit.commit()

        val result = transactionServer.scanTransactions(
          streamID,
          stream.partitions,
          0L,
          4L,
          Int.MaxValue,
          Set(TransactionStates.Opened)
        )

        result.producerTransactions should contain theSameElementsAs Seq(producerTransactionsWithTimestamp(1)._1, producerTransactionsWithTimestamp(6)._1)
        result.lastOpenedTransactionID shouldBe 3L
      }
    }
  }

  it should "correctly return producerTransactions until first opened and not checkpointed transaction on: A <= LT < B: " +
    "return (LT, AvailableTransactions[A, LT]), where A - from transaction bound, B - to transaction bound" in {
    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>
      val streamsNumber = 5

      val streams = Array.fill(streamsNumber)(getRandomStream)
      val streamsAndIDs = streams.map(stream =>
        (transactionServer.putStream(stream.name, stream.partitions, stream.description, stream.ttl), stream)
      )


      streamsAndIDs foreach { case (streamId, stream) =>
        val currentTimeInc = new AtomicLong(System.currentTimeMillis())
        val transactionRootChain = getRandomProducerTransaction(streamId, stream, 1, Long.MaxValue)
        val producerTransactionsWithTimestamp: Array[(ProducerTransaction, Long)] =
          Array(
            (transactionRootChain, currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 1L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 5L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 5L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement())
          )

        val transactionsWithTimestamp =
          producerTransactionsWithTimestamp.map {
            case (producerTxn, timestamp) => ProducerTransactionRecord(producerTxn, timestamp)
          }

        val bigCommit = transactionServer.getBigCommit(1L)
        bigCommit.putProducerTransactions(transactionsWithTimestamp)
        bigCommit.commit()

        val result = transactionServer.scanTransactions(
          streamId,
          stream.partitions,
          0L,
          5L,
          Int.MaxValue,
          Set(TransactionStates.Opened)
        )

        result.producerTransactions should contain theSameElementsAs Seq(producerTransactionsWithTimestamp(1)._1)
        result.lastOpenedTransactionID shouldBe 5L
      }
    }
  }

  it should "correctly return producerTransactions on: LT >= B: " +
    "return (LT, AvailableTransactions[A, B]), where A - from transaction bound, B - to transaction bound" in {
    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>
      val streamsNumber = 5

      val streams = Array.fill(streamsNumber)(getRandomStream)
      val streamsAndIDs = streams.map(stream =>
        (transactionServer.putStream(stream.name, stream.partitions, stream.description, stream.ttl), stream)
      )


      streamsAndIDs foreach { case (streamId, stream) =>
        val currentTimeInc = new AtomicLong(System.currentTimeMillis())
        val transactionRootChain = getRandomProducerTransaction(streamId, stream, 1, Long.MaxValue)
        val producerTransactionsWithTimestamp: Array[(ProducerTransaction, Long)] =
          Array(
            (transactionRootChain, currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 1L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 4L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 5L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement())
          )

        val transactionsWithTimestamp =
          producerTransactionsWithTimestamp.map {
            case (producerTxn, timestamp) => ProducerTransactionRecord(producerTxn, timestamp)
          }

        val bigCommit = transactionServer.getBigCommit(1L)
        bigCommit.putProducerTransactions(transactionsWithTimestamp)
        bigCommit.commit()

        val result1 = transactionServer.scanTransactions(
          streamId,
          stream.partitions,
          0L,
          4L,
          Int.MaxValue,
          Set(TransactionStates.Opened)
        )
        result1.producerTransactions should contain theSameElementsAs Seq(producerTransactionsWithTimestamp(1)._1, producerTransactionsWithTimestamp(6)._1)
        result1.lastOpenedTransactionID shouldBe 5L

        val result2 = transactionServer.scanTransactions(
          streamId,
          stream.partitions,
          0L,
          5L,
          Int.MaxValue,
          Set(TransactionStates.Opened)
        )
        result2.producerTransactions should contain theSameElementsAs Seq(producerTransactionsWithTimestamp(1)._1, producerTransactionsWithTimestamp(6)._1)
        result2.lastOpenedTransactionID shouldBe 5L
      }
    }
  }

  it should "correctly return producerTransactions with defined count and states(which discard all producers transactions thereby retuning an empty collection of them) on: LT >= B: " +
    "return (LT, AvailableTransactions[A, B]), where A - from transaction bound, B - to transaction bound" in {
    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>
      val streamsNumber = 5

      val streams = Array.fill(streamsNumber)(getRandomStream)
      val streamsAndIDs = streams.map(stream =>
        (transactionServer.putStream(stream.name, stream.partitions, stream.description, stream.ttl), stream)
      )


      streamsAndIDs foreach { case (streamId, stream) =>
        val currentTimeInc = new AtomicLong(System.currentTimeMillis())
        val transactionRootChain = getRandomProducerTransaction(streamId, stream, 1, Long.MaxValue)
        val producerTransactionsWithTimestamp: Array[(ProducerTransaction, Long)] =
          Array(
            (transactionRootChain, currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 1L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 4L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 5L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement())
          )

        val transactionsWithTimestamp =
          producerTransactionsWithTimestamp.map {
            case (producerTxn, timestamp) => ProducerTransactionRecord(producerTxn, timestamp)
          }

        val bigCommit = transactionServer.getBigCommit(1L)
        bigCommit.putProducerTransactions(transactionsWithTimestamp)
        bigCommit.commit()

        val result2 = transactionServer.scanTransactions(
          streamId,
          stream.partitions,
          0L,
          5L,
          0,
          Set(TransactionStates.Opened)
        )
        result2.producerTransactions shouldBe empty
        result2.lastOpenedTransactionID shouldBe 5L
      }
    }
  }

  it should "correctly return producerTransactions with defined count and states(which accepts all producers transactions thereby retuning all of them) on: LT >= B: " +
    "return (LT, AvailableTransactions[A, B]), where A - from transaction bound, B - to transaction bound" in {
    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>
      val streamsNumber = 5

      val streams = Array.fill(streamsNumber)(getRandomStream)
      val streamsAndIDs = streams.map(stream =>
        (transactionServer.putStream(stream.name, stream.partitions, stream.description, stream.ttl), stream)
      )


      streamsAndIDs foreach { case (streamId, stream) =>
        val currentTimeInc = new AtomicLong(System.currentTimeMillis())
        val transactionRootChain = getRandomProducerTransaction(streamId, stream, 1, Long.MaxValue)
        val producerTransactionsWithTimestamp: Array[(ProducerTransaction, Long)] =
          Array(
            (transactionRootChain, currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 1L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 2L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 3L, state = TransactionStates.Checkpointed), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 4L, state = TransactionStates.Updated), currentTimeInc.getAndIncrement()),
            (transactionRootChain.copy(transactionID = 5L, state = TransactionStates.Opened), currentTimeInc.getAndIncrement())
          )

        val transactionsWithTimestamp =
          producerTransactionsWithTimestamp.map { case (producerTxn, timestamp) => ProducerTransactionRecord(producerTxn, timestamp) }

        val bigCommit = transactionServer.getBigCommit(1L)

        bigCommit.putProducerTransactions(transactionsWithTimestamp)
        bigCommit.commit()

        val result2 = transactionServer.scanTransactions(
          streamId,
          stream.partitions,
          0L,
          5L,
          5,
          Set(TransactionStates.Opened)
        )
        result2.producerTransactions should contain theSameElementsAs Seq(producerTransactionsWithTimestamp(1)._1, producerTransactionsWithTimestamp(6)._1)
        result2.lastOpenedTransactionID shouldBe 5L
      }
    }
  }

  it should "return all transactions if no incomplete" in {
    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>
      val stream = getRandomStream
      val streamID = transactionServer.putStream(
        stream.name,
        stream.partitions,
        stream.description,
        stream.ttl
      )

      val ALL = 80
      var currentTime = System.currentTimeMillis()
      val transactions = for (i <- 0 until ALL) yield {
        currentTime = currentTime + 1L
        currentTime
      }
      val firstTransaction = transactions.head
      val lastTransaction = transactions.last


      val partition = 1
      val txns = transactions.flatMap { t =>
        Seq(
          ProducerTransactionRecord(streamID, partition, t, TransactionStates.Opened, 1, 120L, t),
          ProducerTransactionRecord(streamID, partition, t, TransactionStates.Checkpointed, 1, 120L, t)
        )
      }

      val bigCommit1 = transactionServer.getBigCommit(1L)
      bigCommit1.putProducerTransactions(txns)
      bigCommit1.commit()

      val res = transactionServer.scanTransactions(
        streamID,
        partition,
        firstTransaction,
        lastTransaction,
        Int.MaxValue,
        Set(TransactionStates.Opened)
      )

      res.producerTransactions.size shouldBe transactions.size

    }
  }


  it should "return only transactions up to 1st incomplete(transaction after Opened one)" in {
    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>
      val streamsNumber = 1

      val streams = Array.fill(streamsNumber)(getRandomStream)
      val streamsAndIDs = streams.map(stream =>
        (transactionServer.putStream(stream.name, stream.partitions, stream.description, stream.ttl), stream)
      )


      streamsAndIDs foreach { case (streamID, stream) =>
        val FIRST = 30
        val LAST = 100
        val partition = 1

        var currentTime = System.currentTimeMillis()
        val transactions1 = for (i <- 0 until FIRST) yield {
          currentTime = currentTime + 1L
          currentTime
        }


        val bigCommit1 = transactionServer.getBigCommit(1L)
        bigCommit1.putProducerTransactions(transactions1.flatMap { t =>
          Seq(
            ProducerTransactionRecord(streamID, partition, t, TransactionStates.Opened, 1, 120L, t),
            ProducerTransactionRecord(streamID, partition, t, TransactionStates.Checkpointed, 1, 120L, t)
          )
        })
        bigCommit1.commit()


        val bigCommit2 = transactionServer.getBigCommit(2L)
        bigCommit2.putProducerTransactions(
          Seq(
            ProducerTransactionRecord(streamID, partition, currentTime, TransactionStates.Opened, 1, 120L, currentTime)
          )
        )

        bigCommit2.commit()

        val transactions2 = for (i <- FIRST until LAST) yield {
          currentTime = currentTime + 1L
          currentTime
        }

        val bigCommit3 = transactionServer.getBigCommit(3L)
        bigCommit3.putProducerTransactions(transactions1.flatMap { t =>
          Seq(
            ProducerTransactionRecord(streamID, partition, t, TransactionStates.Opened, 1, 120L, t),
            ProducerTransactionRecord(streamID, partition, t, TransactionStates.Checkpointed, 1, 120L, t)
          )
        })
        bigCommit3.commit()

        val transactions = transactions1 ++ transactions2
        val firstTransaction = transactions.head
        val lastTransaction = transactions.last

        val res = transactionServer.scanTransactions(
          streamID,
          partition,
          firstTransaction,
          lastTransaction,
          Int.MaxValue,
          Set(TransactionStates.Opened)
        )
        res.producerTransactions.size shouldBe transactions1.size
      }
    }
  }

  it should "return none if empty" in {
    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>

      val stream = getRandomStream
      val streamID = transactionServer.putStream(
        stream.name,
        stream.partitions,
        stream.description,
        stream.ttl
      )

      val ALL = 100
      var currentTime = System.currentTimeMillis()
      val transactions = for (i <- 0 until ALL) yield {
        currentTime = currentTime + 1L
        currentTime
      }

      val firstTransaction = transactions.head
      val lastTransaction = transactions.last
      val res = transactionServer.scanTransactions(
        streamID,
        1,
        firstTransaction,
        lastTransaction,
        Int.MaxValue,
        Set(TransactionStates.Opened)
      )
      res.producerTransactions.size shouldBe 0

    }
  }

  it should "return none if to < from" in {
    val bundle = util.Utils
      .getTransactionServerBundle(zkClient)

    bundle.operate { transactionServer =>

      val stream = getRandomStream
      val streamID = transactionServer.putStream(
        stream.name,
        stream.partitions,
        stream.description,
        stream.ttl
      )

      val ALL = 80

      var currentTime = System.currentTimeMillis()
      val transactions = for (i <- 0 until ALL) yield {
        currentTime = currentTime + 1L
        currentTime
      }
      val firstTransaction = transactions.head
      val lastTransaction = transactions.tail.tail.tail.head

      val bigCommit1 = transactionServer.getBigCommit(1L)
      bigCommit1.putProducerTransactions(transactions.flatMap { t =>
        Seq(ProducerTransactionRecord(streamID, 1, t, TransactionStates.Opened, 1, 120L, t))
      })
      bigCommit1.commit()


      val res = transactionServer.scanTransactions(
        streamID,
        1,
        lastTransaction,
        firstTransaction,
        Int.MaxValue,
        Set(TransactionStates.Opened)
      )
      res.producerTransactions.size shouldBe 0

    }
  }
}

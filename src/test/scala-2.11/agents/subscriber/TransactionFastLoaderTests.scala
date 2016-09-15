package agents.subscriber

import java.util.UUID
import java.util.concurrent.CountDownLatch

import com.bwsw.tstreams.agents.consumer.subscriber.{Callback, TransactionFastLoader, TransactionState}
import com.bwsw.tstreams.agents.consumer.{ConsumerTransaction, TransactionOperator}
import com.bwsw.tstreams.common.FirstFailLockableTaskExecutor
import com.bwsw.tstreams.coordination.messages.state.TransactionStatus
import com.datastax.driver.core.utils.UUIDs
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

trait FastLoaderTestContainer {
  val lastTransactionsMap = mutable.Map[Int, TransactionState]()
  val fastLoader = new TransactionFastLoader(partitions(), lastTransactionsMap)

  def partitions() = Set(0)

  def test()
}

class FastLoaderOperatorTestImpl extends TransactionOperator[String] {
  val TOTAL = 10
  val transactions = new ListBuffer[ConsumerTransaction[String]]()
  for (i <- 0 until TOTAL)
    transactions += new ConsumerTransaction[String](0, UUIDs.timeBased(), 1, -1)

  override def getLastTransaction(partition: Int): Option[ConsumerTransaction[String]] = None

  override def getTransactionById(partition: Int, uuid: UUID): Option[ConsumerTransaction[String]] = None

  override def setStreamPartitionOffset(partition: Int, uuid: UUID): Unit = {}

  override def loadTransactionFromDB(partition: Int, transaction: UUID): Option[ConsumerTransaction[String]] = None

  override def getTransactionsFromTo(partition: Int, from: UUID, to: UUID): ListBuffer[ConsumerTransaction[String]] =
    transactions

  override def checkpoint(): Unit = {}

  override def getPartitions(): Set[Int] = Set[Int](0)

  override def getCurrentOffset(partition: Int): UUID = UUIDs.timeBased()

  override def buildTransactionObject(partition: Int, uuid: UUID, count: Int): Option[ConsumerTransaction[String]] = Some(new ConsumerTransaction[String](0, UUIDs.timeBased(), 1, -1))
}

/**
  * Created by Ivan Kudryavtsev on 21.08.16.
  */
class TransactionFastLoaderTests extends FlatSpec with Matchers {
  it should "load fast if next state is after prev state from the same master" in {
    val tc = new FastLoaderTestContainer {
      val partition = 0
      val masterID = 0
      val orderID = 0
      lastTransactionsMap(0) = TransactionState(UUIDs.timeBased(), partition, masterID, orderID, 1, TransactionStatus.postCheckpoint, -1)
      val nextTransactionState = List(TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 1, 1, TransactionStatus.postCheckpoint, -1))

      override def test(): Unit = {
        fastLoader.checkIfTransactionLoadingIsPossible(nextTransactionState) shouldBe true
      }
    }

    tc.test()
  }

  it should "load fast if next 3 states are ordered after prev state from the same master" in {
    val tc = new FastLoaderTestContainer {
      val partition = 0
      val masterID = 0
      val orderID = 0
      lastTransactionsMap(0) = TransactionState(UUIDs.timeBased(), partition, masterID, orderID, 1, TransactionStatus.postCheckpoint, -1)
      val nextTransactionState = List(
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 1, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 2, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 3, 1, TransactionStatus.postCheckpoint, -1))

      override def test(): Unit = {
        fastLoader.checkIfTransactionLoadingIsPossible(nextTransactionState) shouldBe true
      }
    }

    tc.test()
  }

  it should "not load fast if next 3 states are not strictly ordered after prev state from the same master" in {
    val tc = new FastLoaderTestContainer {
      val partition = 0
      val masterID = 0
      val orderID = 0
      lastTransactionsMap(0) = TransactionState(UUIDs.timeBased(), partition, masterID, orderID, 1, TransactionStatus.postCheckpoint, -1)
      val nextTransactionState = List(
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 1, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 2, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 2, 1, TransactionStatus.postCheckpoint, -1))

      override def test(): Unit = {
        fastLoader.checkIfTransactionLoadingIsPossible(nextTransactionState) shouldBe false
      }
    }

    tc.test()
  }

  it should "not load fast if state after prev is not ordered" in {
    val tc = new FastLoaderTestContainer {
      val partition = 0
      val masterID = 0
      val orderID = 0
      lastTransactionsMap(0) = TransactionState(UUIDs.timeBased(), partition, masterID, orderID, 1, TransactionStatus.postCheckpoint, -1)
      val nextTransactionState = List(
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID, 1, TransactionStatus.postCheckpoint, -1))

      override def test(): Unit = {
        fastLoader.checkIfTransactionLoadingIsPossible(nextTransactionState) shouldBe false
      }
    }

    tc.test()
  }

  it should "not load fast if state after prev is ordered but master differs" in {
    val tc = new FastLoaderTestContainer {
      val partition = 0
      val masterID = 0
      val orderID = 0
      lastTransactionsMap(0) = TransactionState(UUIDs.timeBased(), partition, masterID, orderID, 1, TransactionStatus.postCheckpoint, -1)
      val nextTransactionState = List(
        TransactionState(UUIDs.timeBased(), partition, masterID + 1, orderID + 1, 1, TransactionStatus.postCheckpoint, -1))

      override def test(): Unit = {
        fastLoader.checkIfTransactionLoadingIsPossible(nextTransactionState) shouldBe false
      }
    }

    tc.test()
  }

  it should "not load fast if next 3 states are strictly ordered after prev state from not the same master" in {
    val tc = new FastLoaderTestContainer {
      val partition = 0
      val masterID = 0
      val orderID = 0
      lastTransactionsMap(0) = TransactionState(UUIDs.timeBased(), partition, masterID + 1, orderID, 1, TransactionStatus.postCheckpoint, -1)
      val nextTransactionState = List(
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 1, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 2, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 2, 1, TransactionStatus.postCheckpoint, -1))

      override def test(): Unit = {
        fastLoader.checkIfTransactionLoadingIsPossible(nextTransactionState) shouldBe false
      }
    }

    tc.test()
  }

  it should "not load fast if next 3 states are strictly ordered after prev state from not the same master - case 2" in {
    val tc = new FastLoaderTestContainer {
      val partition = 0
      val masterID = 0
      val orderID = 0
      lastTransactionsMap(0) = TransactionState(UUIDs.timeBased(), partition, masterID, orderID, 1, TransactionStatus.postCheckpoint, -1)
      val nextTransactionState = List(
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 1, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 2, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID + 1, orderID + 3, 1, TransactionStatus.postCheckpoint, -1))

      override def test(): Unit = {
        fastLoader.checkIfTransactionLoadingIsPossible(nextTransactionState) shouldBe false
      }
    }

    tc.test()
  }

  it should "load one transaction if check is ok" in {
    val tc = new FastLoaderTestContainer {
      val partition = 0
      val masterID = 0
      val orderID = 0
      lastTransactionsMap(0) = TransactionState(UUIDs.timeBased(), partition, masterID, orderID, 1, TransactionStatus.postCheckpoint, -1)
      val nextTransactionState = List(
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 1, 1, TransactionStatus.postCheckpoint, -1))
      var ctr: Int = 0
      val l = new CountDownLatch(1)

      override def test(): Unit = {
        fastLoader.load[String](nextTransactionState, new FastLoaderOperatorTestImpl, new FirstFailLockableTaskExecutor("lf"), new Callback[String] {
          override def onTransaction(consumer: TransactionOperator[String], transaction: ConsumerTransaction[String]): Unit = {
            ctr += 1
            l.countDown()
          }
        })
        l.await()
        ctr shouldBe 1
        lastTransactionsMap(0).uuid shouldBe nextTransactionState.head.uuid
      }
    }

    tc.test()
  }

  it should "load three transactions if check is ok" in {
    val tc = new FastLoaderTestContainer {
      val partition = 0
      val masterID = 0
      val orderID = 0
      lastTransactionsMap(0) = TransactionState(UUIDs.timeBased(), partition, masterID, orderID, 1, TransactionStatus.postCheckpoint, -1)
      val nextTransactionState = List(
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 1, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 2, 1, TransactionStatus.postCheckpoint, -1),
        TransactionState(UUIDs.timeBased(), partition, masterID, orderID + 3, 1, TransactionStatus.postCheckpoint, -1))
      var ctr: Int = 0
      val l = new CountDownLatch(1)

      override def test(): Unit = {
        fastLoader.load[String](nextTransactionState, new FastLoaderOperatorTestImpl, new FirstFailLockableTaskExecutor("lf"), new Callback[String] {
          override def onTransaction(consumer: TransactionOperator[String], transaction: ConsumerTransaction[String]): Unit = {
            ctr += 1
            if (ctr == 3)
              l.countDown()
          }
        })
        l.await()
        ctr shouldBe 3
        lastTransactionsMap(0).uuid shouldBe nextTransactionState.last.uuid
      }
    }

    tc.test()
  }

}

package com.bwsw.tstreams.agents.consumer.subscriber

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import com.bwsw.tstreams.coordination.subscribe.ConsumerCoordinator
import com.bwsw.tstreams.coordination.subscribe.messages.{ProducerTopicMessage, ProducerTransactionStatus}
import ProducerTransactionStatus._
import com.bwsw.tstreams.txnqueue.PersistentTransactionQueue
import scala.util.control.Breaks._


/**
 * Class for consuming transactions on concrete partition from concrete offset
 * @param subscriber Subscriber instance which instantiate this relay
 * @param offset Offset from which to start
 * @param partition Partition from which to consume
 * @param coordinator Coordinator instance for maintaining new transactions updates
 * @param callback Callback on consumed transactions
 * @param queue Queue for maintain consumed transactions
 * @tparam DATATYPE
 * @tparam USERTYPE
 */
class SubscriberTransactionsRelay[DATATYPE,USERTYPE](subscriber : BasicSubscribingConsumer[DATATYPE,USERTYPE],
                                                     offset: UUID,
                                                     partition : Int,
                                                     coordinator: ConsumerCoordinator,
                                                     callback: BasicSubscriberCallback[DATATYPE, USERTYPE],
                                                     queue : PersistentTransactionQueue) {


  private val transactionBuffer  = new TransactionsBuffer
  private val lock = new ReentrantLock(true)
  private var lastConsumedTransaction : UUID = subscriber.options.txnGenerator.getTimeUUID(0)
  private val streamName = subscriber.stream.getName
  private val isRunning = new AtomicBoolean(true)
  private val updateCallback = (msg : ProducerTopicMessage) => {
    if (msg.partition == partition) {
      lock.lock()
      if (msg.txnUuid.timestamp() > lastConsumedTransaction.timestamp())
        transactionBuffer.update(msg.txnUuid, msg.status, msg.ttl)
      lock.unlock()
    }
  }

  private var queueConsumer : Thread = null
  private var transactionsConsumerBeforeLast : Thread = null
  private var updateThread : Thread = null

  /**
   * Start consume transaction queue async
   */
  def startConsumeAndCallbackQueueAsync() = {
    val latch = new CountDownLatch(1)
    queueConsumer = new Thread(new Runnable {
      override def run(): Unit = {
        latch.countDown()
        while (isRunning.get()) {
          val txn = queue.get()
          callback.onEvent(subscriber, partition, txn)
        }
      }
    })
    queueConsumer.start()
    latch.await()
  }

  /**
   * Consume all transactions in interval (offset;transactionUUID]
   * @param transactionUUID Right border to consume
   */
  //TODO check that queue correctly sorted
  def consumeTransactionsLessOrEqualThanAsync(transactionUUID : UUID) = {
    val latch = new CountDownLatch(1)

    //TODO DEBUG ONLY
    var lasttxn : UUID = null

    transactionsConsumerBeforeLast = new Thread(new Runnable {
      override def run(): Unit = {
        latch.countDown()

        val transactions = subscriber.stream.metadataStorage.commitEntity.getTransactionsMoreThanAndLessOrEqualThan(
          streamName = streamName,
          partition = partition,
          leftBorder = offset,
          rightBorder = transactionUUID)

        while (transactions.nonEmpty && isRunning.get()) {
          val uuid = transactions.dequeue().txnUuid
          queue.put(uuid)
          lasttxn = uuid
        }

        //TODO DEBUG ONLY
        if (lasttxn!=null)
          assert(lasttxn.timestamp() == transactionUUID.timestamp())
      }
    })
    transactionsConsumerBeforeLast.start()
    latch.await()
  }

  /**
   * Consume all transaction starting from transactionUUID without including it
   * @param transactionUUID Left border to consume
   */
  def consumeTransactionsMoreThan(transactionUUID : UUID) = {
    val messagesGreaterThanLast =
        subscriber.stream.metadataStorage.commitEntity.getTransactionsMoreThan(
          streamName,
          partition,
          transactionUUID)

    lock.lock()
    messagesGreaterThanLast foreach { m =>
      transactionBuffer.update(m.txnUuid, ProducerTransactionStatus.closed, m.ttl)
    }
    lock.unlock()
  }

  /**
   * Update producers subscribers info
   * @return Listener ID
   */
  def notifyProducers() : Unit = {
    coordinator.addCallback(updateCallback)
    coordinator.registerSubscriber(subscriber.stream.getName, partition)
    coordinator.notifyProducers(subscriber.stream.getName, partition)
  }

  /**
   * Start pushing data in persistent queue from transaction buffer
   */
  def startUpdate() : Unit = {
    val latch = new CountDownLatch(1)

    updateThread =
    new Thread(new Runnable {
      override def run(): Unit = {
        latch.countDown()

        //start handling map
        while (isRunning.get()) {
          lock.lock()

          val it = transactionBuffer.getIterator()
          breakable {
            while (it.hasNext) {
              val entry = it.next()
              val key: UUID = entry.getKey
              val (status: ProducerTransactionStatus, _) = entry.getValue
              status match {
                case ProducerTransactionStatus.opened =>
                  break()
                case ProducerTransactionStatus.closed =>
                  queue.put(key)
              }

              //TODO remove after complex testing
              if (lastConsumedTransaction.timestamp() >= key.timestamp())
                throw new IllegalStateException("incorrect subscriber state")

              lastConsumedTransaction = key
              it.remove()
            }
          }

          lock.unlock()
          Thread.sleep(callback.frequency * 1000L)
        }

      }
    })
    updateThread.start()
    latch.await()
  }

  def stop() = {
    isRunning.set(false)
    updateThread.join()
    transactionsConsumerBeforeLast.join()
    queueConsumer.join()
  }
}

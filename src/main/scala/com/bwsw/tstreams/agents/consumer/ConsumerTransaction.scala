package com.bwsw.tstreams.agents.consumer

import scala.collection.mutable

/**
  *
  * @param partition
  * @param transactionID
  * @param count
  * @param ttl
  */
class ConsumerTransaction(partition: Int,
                             transactionID: Long,
                             count: Int,
                             ttl: Int) {

  override def toString(): String = {
    s"consumer.Transaction(id=$transactionID,partition=$partition, count=$count, ttl=$ttl)"
  }

  var consumer: Consumer = null

  def attach(c: Consumer) = {
    if (c == null)
      throw new IllegalArgumentException("Argument must be not null.")

    if (consumer == null)
      consumer = c
    else
      throw new IllegalStateException("The transaction is already attached to consumer")
  }

  /**
    * Return transaction ID
    */
  def getTransactionID() = transactionID

  /**
    * Return transaction partition
    */
  def getPartition(): Int = partition

  /**
    * Return count of items in transaction
    */
  def getCount(): Int = count

  /**
    * Return TTL
    */
  def getTTL(): Int = ttl

  /**
    * Transaction data pointer
    */
  private var cnt = 0

  /**
    * Buffer to preload some amount of current transaction data
    */
  private var buffer: scala.collection.mutable.Queue[Array[Byte]] = null

  /**
    * @return Next piece of data from current transaction
    */
  def next() = this.synchronized {

    if (consumer == null)
      throw new IllegalArgumentException("Transaction is not yet attached to consumer. Attach it first.")

    if (!hasNext())
      throw new IllegalStateException("There is no data to receive from data storage")

    //try to update buffer
    if (buffer == null || buffer.isEmpty) {
      val newCount = min2(cnt + consumer.options.dataPreload, count - 1)
      buffer = consumer.stream.dataStorage.get(consumer.stream.name, partition, transactionID, cnt, newCount)
      cnt = newCount + 1
    }

    buffer.dequeue()
  }

  /**
    * Indicate consumed or not current transaction
    *
    * @return
    */
  def hasNext(): Boolean = this.synchronized {
    cnt < count || buffer.nonEmpty
  }

  /**
    * Refresh BasicConsumerTransaction iterator to read from the beginning
    */
  def replay(): Unit = this.synchronized {
    buffer.clear()
    cnt = 0
  }

  /**
    * @return All consumed transaction
    */
  def getAll() = this.synchronized {

    if (consumer == null)
      throw new IllegalArgumentException("Transaction is not yet attached to consumer. Attach it first.")

    val data: mutable.Queue[Array[Byte]] = consumer.stream.dataStorage.get(consumer.stream.name, partition, transactionID, cnt, count - 1)
    data
  }

  /**
    * Helper function to find min value
    *
    * @param a First value
    * @param b Second value
    * @return Min value
    */
  private def min2(a: Int, b: Int): Int = if (a < b) a else b
}
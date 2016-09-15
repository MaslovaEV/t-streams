package com.bwsw.tstreams.agents.consumer.subscriber

import com.bwsw.tstreams.agents.consumer.{ConsumerTransaction, TransactionOperator}

/**
  * Trait to implement to handle incoming messages
  */
trait Callback[T] {
  /**
    * Callback which is called on every closed transaction
    *
    * @param consumer    associated Consumer
    * @param transaction the transaction which currently has delivered
    */
  def onTransaction(consumer: TransactionOperator[T],
                    transaction: ConsumerTransaction[T]): Unit

  /**
    *
    * @param consumer  consumer object which is associated with the transaction
    * @param partition partition on which the transaction is
    * @param uuid      transaction uuid
    * @param count     amount of data items inside of the transaction
    */
  def onTransactionCall(consumer: TransactionOperator[T],
                        partition: Int,
                        uuid: java.util.UUID,
                        count: Int) = {
    val transactionOpt = consumer.buildTransactionObject(partition, uuid, count)
    transactionOpt.foreach(transaction => onTransaction(consumer, transaction = transaction))
  }
}

package com.bwsw.tstreams.agents.consumer



/**
 * Trait to implement to handle incoming messages
 */
trait BasicConsumerCallback {
  /**
   * Callback which is called on every closed partition/transaction
   * @param partition partition of the incoming transaction
   * @param transactionUuid time uuid of the incoming transaction
   */
  def onEvent(partition : Int, transactionUuid : java.util.UUID) : Unit

  /**
   * Frequency of handling incoming transactions in seconds
   */
  val frequency : Int
}
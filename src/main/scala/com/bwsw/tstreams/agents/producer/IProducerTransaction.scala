package com.bwsw.tstreams.agents.producer

import com.bwsw.tstreams.agents.group.ProducerTransactionStateInfo
import com.bwsw.tstreams.proto.protocol.TransactionState

/**
  * Created by Ivan Kudryavtsev on 29.08.16.
  */
trait IProducerTransaction {

  def send(obj: Array[Byte]): IProducerTransaction

  def finalizeDataSend(): Unit

  def cancel(): Unit

  def checkpoint(): Unit

  def getStateInfo(status: TransactionState.Status): ProducerTransactionStateInfo

  private[tstreams] def getUpdateInfo: Option[RPCProducerTransaction]

  private[tstreams] def getCancelInfoAndClose: Option[RPCProducerTransaction]

  private[tstreams] def notifyCancelEvent()

  private[tstreams] def notifyUpdate(): Unit

  def isClosed: Boolean

  def getTransactionID: Long

  def markAsClosed(): Unit
}

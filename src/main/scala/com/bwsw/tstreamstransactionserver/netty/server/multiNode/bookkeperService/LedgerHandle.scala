package com.bwsw.tstreamstransactionserver.netty.server.multiNode.bookkeperService

import com.bwsw.tstreamstransactionserver.netty.server.multiNode.bookkeperService.data.{Record, RecordWithIndex}

abstract class LedgerHandle(val id: Long) {
  def addRecord(data: Record): Long

  def getRecord(id: Long): Record

  def readRecords(from: Long, to: Long): Array[Record]

  def getOrderedRecords(from: Long): Array[RecordWithIndex]

  def lastRecord(): Option[Record]

  def lastRecordID(): Long

  def close(): Unit
}

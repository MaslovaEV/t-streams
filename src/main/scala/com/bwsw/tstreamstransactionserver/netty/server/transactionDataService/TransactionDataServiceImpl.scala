package com.bwsw.tstreamstransactionserver.netty.server.transactionDataService

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import com.bwsw.tstreamstransactionserver.`implicit`.Implicits._
import com.bwsw.tstreamstransactionserver.configProperties.ServerExecutionContext
import com.bwsw.tstreamstransactionserver.netty.server.db.rocks.RocksDbConnection
import com.bwsw.tstreamstransactionserver.netty.server.{Authenticable, StreamCache}
import com.bwsw.tstreamstransactionserver.options.ServerOptions.{RocksStorageOptions, StorageOptions}
import org.slf4j.{Logger, LoggerFactory}
import transactionService.rpc.TransactionDataService

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future => ScalaFuture}

trait TransactionDataServiceImpl extends TransactionDataService[ScalaFuture]
  with Authenticable
  with StreamCache {

  val executionContext: ServerExecutionContext
  val storageOpts: StorageOptions
  val rocksStorageOpts: RocksStorageOptions

//  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val ttlToAdd: Int = rocksStorageOpts.ttlAddMs

  private def calculateTTL(ttl: Long): Int = {
    def convertTTL = {
      val ttlToConvert = TimeUnit.MILLISECONDS.toSeconds(ttlToAdd).toInt
      if (ttlToConvert == 0) 0 else ttlToConvert
    }

    TimeUnit.HOURS.toSeconds(ttl).toInt + convertTTL
  }

  val rocksDBStorageToStream = new java.util.concurrent.ConcurrentHashMap[StorageName, RocksDbConnection]()

  private def getStorage(stream: String, ttl: Long) = {
    val key = StorageName(stream)
    rocksDBStorageToStream.computeIfAbsent(key, (t: StorageName) => {
      new RocksDbConnection(storageOpts, rocksStorageOpts, key.toString, calculateTTL(ttl))
    })
  }

  override def putTransactionData(token: Int, stream: String, partition: Int, transaction: Long, data: Seq[ByteBuffer], from: Int): ScalaFuture[Boolean] = {
    val streamObj = getStreamDatabaseObject(stream)
    val rocksDB = getStorage(stream, streamObj.stream.ttl)
    authenticate(token) {
      val batch = rocksDB.newBatch

      val rangeDataToSave = from until (from + data.length)
      val keys = rangeDataToSave map (seqId => KeyDataSeq(Key(partition, transaction), seqId).toBinary)
      (keys zip data) foreach { case (key, datum) =>
        val sizeOfSlicedData = datum.limit() - datum.position()
        val bytes = new Array[Byte](sizeOfSlicedData)
        datum.get(bytes)
        batch.put(key, bytes)
      }
      val isOkay = batch.write()

//      if (isOkay)
//        logger.debug(s"$stream $partition $transaction. Successfully saved transaction data.")
//      else
//        logger.debug(s"$stream $partition $transaction. Transaction data hasn't been saved.")

      isOkay
    }(executionContext.rocksWriteContext)
  }


  override def getTransactionData(token: Int, stream: String, partition: Int, transaction: Long, from: Int, to: Int): ScalaFuture[Seq[ByteBuffer]] = {
    val streamObj = getStreamDatabaseObject(stream)
    val rocksDB = getStorage(stream, streamObj.stream.ttl)
    authenticate(token) {
      val fromSeqId = KeyDataSeq(Key(partition, transaction), from).toBinary
      val toSeqId = KeyDataSeq(Key(partition, transaction), to).toBinary

      val iterator = rocksDB.iterator
      iterator.seek(fromSeqId)

      val data = new ArrayBuffer[ByteBuffer](to - from)
      while (iterator.isValid && ByteArray.compare(iterator.key(), toSeqId) <= 0) {
        data += java.nio.ByteBuffer.wrap(iterator.value())
        iterator.next()
      }
      iterator.close()
      data
    }(executionContext.rocksReadContext)
  }

  def closeTransactionDataDatabases() = rocksDBStorageToStream.values().forEach(_.close())
}
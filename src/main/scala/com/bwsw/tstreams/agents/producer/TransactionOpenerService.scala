package com.bwsw.tstreams.agents.producer

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import com.bwsw.tstreams.common.ProtocolMessageSerializer
import com.bwsw.tstreams.common.ProtocolMessageSerializer.ProtocolMessageSerializerException
import com.bwsw.tstreams.coordination.client.TcpNewTransactionServer
import com.bwsw.tstreams.coordination.messages.master._
import io.netty.channel.Channel
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.leader.LeaderLatch
import org.apache.zookeeper.KeeperException
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Random

/**
  *
  */
object TransactionOpenerService {

  /**
    * How long to sleep during master elections actions
    */
  val RETRY_SLEEP_TIME = 100
  /**
    * Generic logger object
    */
  val logger = LoggerFactory.getLogger(this.getClass)

}

/**
  * Agent for providing peer to peer interaction between Producers
  *
  * @param curatorClient
  * @param peerKeepAliveTimeout
  * @param producer       Producer reference
  * @param usedPartitions List of used producer partitions
  * @param transport      Transport to provide interaction
  */
class TransactionOpenerService(curatorClient: CuratorFramework,
                               peerKeepAliveTimeout: Int,
                               producer: Producer,
                               usedPartitions: Set[Int],
                               transport: TcpNewTransactionServer,
                               threadPoolAmount: Int) {


  val myInetAddress: String = producer.producerOptions.coordinationOptions.transaportServer.getInetAddress()

  def getSubscriberNotifier() = producer.subscriberNotifier

  /**
    * Job Executors
    */
  private val executorGraphs = mutable.Map[Int, ExecutorGraph]()

  private val streamName = producer.stream.name
  private val isRunning = new AtomicBoolean(true)

  /**
    * this ID is used to track sequential transactions from the same master
    */
  private val uniqueAgentId = Math.abs(Random.nextInt() + 1)

  def getAgentAddress() = myInetAddress

  def getProducer() = producer

  def getUniqueAgentID() = uniqueAgentId

  def getAndIncSequentialID(partition: Int): Long = sequentialIds(partition).getAndIncrement()

  /**
    * this ID map is used to track sequential transactions on subscribers
    */
  private val sequentialIds = mutable.Map[Int, AtomicLong]()

  TransactionOpenerService.logger.info(s"[INIT] Start initialize agent with address: {$myInetAddress}")
  TransactionOpenerService.logger.info(s"[INIT] Stream: {$streamName}, partitions: [${usedPartitions.mkString(",")}]")
  TransactionOpenerService.logger.info(s"[INIT] Master Unique random ID: $uniqueAgentId")

  private val localLeaderMap = mutable.Map[Int, (String, Int)]()
  private val leaderMap = mutable.Map[Int, LeaderLatch]()

  transport.start((s: Channel, m: String) => handleMessage(s, m))

  (0 until threadPoolAmount) foreach { x =>
    executorGraphs(x) = new ExecutorGraph(s"id-$x")
  }

  private val partitionsToExecutors = usedPartitions
    .zipWithIndex
    .map { case (partition, execNum) => (partition, execNum % threadPoolAmount) }
    .toMap


  // fill initial sequential counters
  usedPartitions foreach { p => sequentialIds += (p -> new AtomicLong(0)) }

  @tailrec
  private def getLeaderId(partition: Int): String = {
    try {
      leaderMap(partition).getLeader().getId
    } catch {
      case e: KeeperException =>
        Thread.sleep(50)
        getLeaderId(partition)
    }
  }

  private def updatePartitionMasterInetAddress(partition: Int): Unit = leaderMap.synchronized {
    if (!usedPartitions.contains(partition))
      return

    if (!leaderMap.contains(partition)) {
      try {
        curatorClient.create.forPath(s"/master-$partition")
      } catch {
        case e: KeeperException =>
          if (e.code() != KeeperException.Code.NODEEXISTS)
            throw e
      }

      val leader = new LeaderLatch(curatorClient, s"/master-$partition", s"${transport.getInetAddress()}#$uniqueAgentId")
      leaderMap(partition) = leader
      leader.start()
    }

    var leaderInfo = getLeaderId(partition)
    while (leaderInfo == "") {
      leaderInfo = getLeaderId(partition)
      Thread.sleep(50)
    }
    val parts = leaderInfo.split('#')
    val leaderAddress = parts.head
    val leaderId = Integer.parseInt(parts.tail.head)

    localLeaderMap.synchronized {
      localLeaderMap(partition) = (leaderAddress, leaderId)
    }
  }


  def getPartitionMasterInetAddressLocal(partition: Int): (String, Int) = localLeaderMap.synchronized {
    if (!localLeaderMap.contains(partition)) {
      updatePartitionMasterInetAddress(partition)
    }
    localLeaderMap(partition)
  }

  def isMasterOfPartition(partition: Int): Boolean = leaderMap.synchronized {
    if (!usedPartitions.contains(partition))
      return false

    if (!leaderMap.contains(partition)) {
      updatePartitionMasterInetAddress(partition)
    }
    leaderMap(partition).hasLeadership()
  }


  /**
    * Retrieve new transaction from agent
    *
    * @param partition Transaction partition
    * @return TransactionID
    */
  def generateNewTransaction(partition: Int): Long = this.synchronized {
    val master = getPartitionMasterInetAddressLocal(partition)._1
    if (TransactionOpenerService.logger.isDebugEnabled) {
      TransactionOpenerService.logger.debug(s"[GET TRANSACTION] Start retrieve transaction for agent with address: {$myInetAddress}, stream: {$streamName}, partition: {$partition} from [MASTER: {$master}].")
    }

    val res =
      producer.transactionRequest(master, partition) match {
        case null =>
          updatePartitionMasterInetAddress(partition)
          generateNewTransaction(partition)

        case EmptyResponse(snd, rcv, p) =>
          updatePartitionMasterInetAddress(partition)
          generateNewTransaction(partition)

        case TransactionResponse(snd, rcv, id, p) =>
          if (TransactionOpenerService.logger.isDebugEnabled) {
            TransactionOpenerService.logger.debug(s"[GET TRANSACTION] Finish retrieve transaction for agent with address: $myInetAddress, stream: $streamName, partition: $partition with ID: $id from [MASTER: $master]s")
          }
          id
      }
    res
  }


  /**
    * Stop this agent
    */
  def stop() = {
    TransactionOpenerService.logger.info(s"P2PAgent of ${producer.name} is shutting down.")
    isRunning.set(false)
    leaderMap.foreach(leader => leader._2.close())

    //to avoid infinite polling block
    executorGraphs.foreach(g => g._2.shutdown())

    transport.stopServer()
  }

  def handleMessage(channel: Channel, rawMessage: String): Unit = {
    if (!isRunning.get())
      return

    val agent = this
    try {
      val request: IMessage = ProtocolMessageSerializer.deserialize(rawMessage)
      request.channel = channel
      if (TransactionOpenerService.logger.isDebugEnabled)
        TransactionOpenerService.logger.debug(s"[HANDLER] Start handle msg:{$request} on agent:{$myInetAddress}")
      val task = () => request.run(agent)
      assert(partitionsToExecutors.contains(request.partition))
      val execNo = partitionsToExecutors(request.partition)
      request match {
        case _: NewTransactionRequest => executorGraphs(execNo).submitToNewTransaction("<NewTransactionTask>", task)
        case _ => throw new IllegalStateException(s"Unknown task ${task}")
      }
    } catch {
      case e: ProtocolMessageSerializerException =>
        TransactionOpenerService.logger.warn(s"Message '$rawMessage' cannot be deserialized. Exception is: ${e.getMessage}")
    }
  }

  /**
    * public method which allows to submit delayed task for execution
    *
    * @param task
    * @param partition
    */
  def submitPipelinedTaskToNewTransactionExecutors(partition: Int, task: () => Unit) = {
    val execNum = partitionsToExecutors(partition)
    executorGraphs(execNum).submitToNewTransaction("<NewTransactionTask>", task)
  }

}
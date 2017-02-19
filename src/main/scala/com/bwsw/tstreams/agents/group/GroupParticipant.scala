package com.bwsw.tstreams.agents.group

import java.util.concurrent.locks.ReentrantLock

/**
  * Trait which can be implemented by any producer/consumer to apply group checkpoint
  */
trait GroupParticipant {

  def getAgentName(): String

  /**
    * Agent lock on any actions which has to do with checkpoint
    */
  def getThreadLock(): ReentrantLock

  /**
    * Info to commit
    */
  def getCheckpointInfoAndClear(): List[CheckpointInfo]

}

/**
  * Agent which sends data into transactions
  */
trait SendingAgent {
  def finalizeDataSend(): Unit
}

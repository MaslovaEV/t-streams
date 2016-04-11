package com.bwsw.tstreams.utils

import java.util.UUID

/**
 * Trait for producer/consumer transaction unique UUID generating
 */
trait ITxnGenerator {
  /**
   * @return Txn UUID
   */
  def getTimeUUID() : UUID
}

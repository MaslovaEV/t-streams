package com.bwsw.tstreams.env.defaults

import com.bwsw.tstreams.common.IntMinMaxDefault
import com.bwsw.tstreams.env.ConfigurationOptions

import scala.collection.mutable

/**
  * Created by Ivan Kudryavtsev on 18.02.17.
  */
object TStreamsFactoryProducerDefaults {

  case class PortRange(from: Int, to: Int)

  object Producer {
    val notifyJobsThreadPoolSize = IntMinMaxDefault(1, 32, 1)

    object Transaction {
      val ttlMs = IntMinMaxDefault(500, 300000, 60000)
      val keepAliveMs = IntMinMaxDefault(100, 60000, 6000)
      val batchSize = IntMinMaxDefault(1, 1000, 100)
      val distributionPolicy = ConfigurationOptions.Producer.Transaction.Constants.DISTRIBUTION_POLICY_RR
    }

  }

  def get = {
    val m = mutable.HashMap[String, Any]()
    val co = ConfigurationOptions.Producer

    m(co.notifyJobsThreadPoolSize) = Producer.notifyJobsThreadPoolSize.default
    m(co.Transaction.ttlMs) = Producer.Transaction.ttlMs.default
    m(co.Transaction.keepAliveMs) = Producer.Transaction.keepAliveMs.default
    m(co.Transaction.batchSize) = Producer.Transaction.batchSize.default
    m(co.Transaction.distributionPolicy) = Producer.Transaction.distributionPolicy

    m
  }

}



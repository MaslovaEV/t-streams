package com.bwsw.tstreamstransactionserver.netty.client.api

import org.apache.curator.framework.state.ConnectionState

import scala.concurrent.{Future => ScalaFuture}

trait TTSClient
  extends MetadataDataClientApi
    with StreamClientApi
    with ConsumerClientApi {

  def getCommitLogOffsets(): ScalaFuture[com.bwsw.tstreamstransactionserver.rpc.CommitLogInfo]

  def shutdown(): Unit
}

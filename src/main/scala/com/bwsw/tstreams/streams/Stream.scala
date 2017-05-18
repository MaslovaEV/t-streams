package com.bwsw.tstreams.streams

import com.bwsw.tstreams.env.defaults.TStreamsFactoryStreamDefaults
import com.bwsw.tstreams.storage.StorageClient

/**
  * @param client          Client to storage
  * @param id              stream identifier
  * @param name            Name of the stream
  * @param partitionsCount Number of stream partitions
  * @param ttl             Time of transaction time expiration in seconds
  * @param description     Some additional info about stream
  */
class Stream(var client: StorageClient, val id: Int, val name: String, val partitionsCount: Int, val ttl: Long, val description: String) {
  if (ttl < TStreamsFactoryStreamDefaults.Stream.ttlSec.min)
    throw new IllegalArgumentException(s"The TTL must be greater or equal than ${TStreamsFactoryStreamDefaults.Stream.ttlSec.min} seconds.")

  def replaceStorageClient(newClient: StorageClient) = {
    shutdown()
    client = newClient
  }

  def shutdown() = {
    client.shutdown()
  }
}

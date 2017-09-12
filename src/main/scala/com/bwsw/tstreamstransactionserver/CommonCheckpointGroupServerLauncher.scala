package com.bwsw.tstreamstransactionserver

import com.bwsw.tstreamstransactionserver.netty.server.multiNode.CommonCheckpointGroupServerBuilder
import com.bwsw.tstreamstransactionserver.options.{CommonOptions, SingleNodeServerOptions}
import com.bwsw.tstreamstransactionserver.options.loader.PropertyFileLoader
import com.bwsw.tstreamstransactionserver.options.loader.PropertyFileReader._

object CommonCheckpointGroupServerLauncher
 extends App {
  val propertyFileLoader =
    PropertyFileLoader()

  val serverAuthOptions: SingleNodeServerOptions.AuthenticationOptions =
    loadServerAuthenticationOptions(propertyFileLoader)
  val zookeeperOptions: CommonOptions.ZookeeperOptions =
    loadZookeeperOptions(propertyFileLoader)
  val bootstrapOptions: SingleNodeServerOptions.BootstrapOptions =
    loadBootstrapOptions(propertyFileLoader)
  val commonRoleOptions: SingleNodeServerOptions.CommonRoleOptions =
    loadCommonRoleOptions(propertyFileLoader)
  val checkpointGroupRoleOptions: SingleNodeServerOptions.CheckpointGroupRoleOptions =
    loadCheckpointGroupRoleOptions(propertyFileLoader)
  val serverStorageOptions: SingleNodeServerOptions.StorageOptions =
    loadServerStorageOptions(propertyFileLoader)
  val serverRocksStorageOptions: SingleNodeServerOptions.RocksStorageOptions =
    loadServerRocksStorageOptions(propertyFileLoader)
  val packageTransmissionOptions: SingleNodeServerOptions.TransportOptions =
    loadPackageTransmissionOptions(propertyFileLoader)
  val subscribersUpdateOptions: SingleNodeServerOptions.SubscriberUpdateOptions =
    loadSubscribersUpdateOptions(propertyFileLoader)
  val commonPrefixesOptions =
    loadCommonPrefixesOptions(propertyFileLoader)
  val bookkeeperOptions =
    loadBookkeeperOptions(propertyFileLoader)

  val builder =
    new CommonCheckpointGroupServerBuilder()

  val server = builder
    .withBootstrapOptions(bootstrapOptions)
    .withSubscribersUpdateOptions(subscribersUpdateOptions)
    .withAuthenticationOptions(serverAuthOptions)
    .withCommonRoleOptions(commonRoleOptions)
    .withCheckpointGroupRoleOptions(checkpointGroupRoleOptions)
    .withServerStorageOptions(serverStorageOptions)
    .withServerRocksStorageOptions(serverRocksStorageOptions)
    .withZookeeperOptions(zookeeperOptions)
    .withPackageTransmissionOptions(packageTransmissionOptions)
    .withCommonPrefixesOptions(commonPrefixesOptions)
    .withBookkeeperOptions(bookkeeperOptions)
    .build()

  server.start()
}

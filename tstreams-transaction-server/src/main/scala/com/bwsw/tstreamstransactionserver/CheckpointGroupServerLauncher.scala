/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.bwsw.tstreamstransactionserver

import com.bwsw.tstreamstransactionserver.netty.server.multiNode.cg.CheckpointGroupServerBuilder
import com.bwsw.tstreamstransactionserver.options.loader.PropertyFileLoader
import com.bwsw.tstreamstransactionserver.options.loader.PropertyFileReader._
import com.bwsw.tstreamstransactionserver.options.{CommonOptions, SingleNodeServerOptions}

object CheckpointGroupServerLauncher
  extends App {

  val propertyFileLoader =
    PropertyFileLoader()

  val serverAuthOptions: SingleNodeServerOptions.AuthenticationOptions =
    loadServerAuthenticationOptions(propertyFileLoader)
  val zookeeperOptions: CommonOptions.ZookeeperOptions =
    loadZookeeperOptions(propertyFileLoader)
  val bootstrapOptions: SingleNodeServerOptions.BootstrapOptions =
    loadBootstrapOptions(propertyFileLoader)
  val checkpointGroupRoleOptions: SingleNodeServerOptions.CheckpointGroupRoleOptions =
    loadCheckpointGroupRoleOptions(propertyFileLoader)
  val serverStorageOptions: SingleNodeServerOptions.StorageOptions =
    loadServerStorageOptions(propertyFileLoader)
  val serverRocksStorageOptions: SingleNodeServerOptions.RocksStorageOptions =
    loadServerRocksStorageOptions(propertyFileLoader)
  val packageTransmissionOptions: SingleNodeServerOptions.TransportOptions =
    loadPackageTransmissionOptions(propertyFileLoader)
  val bookkeeperOptions =
    loadBookkeeperOptions(propertyFileLoader)
  val tracingOptions: CommonOptions.TracingOptions = loadTracingOptions(propertyFileLoader)

  val builder =
    new CheckpointGroupServerBuilder()

  val server = builder
    .withBootstrapOptions(bootstrapOptions)
    .withAuthenticationOptions(serverAuthOptions)
    .withCheckpointGroupRoleOptions(checkpointGroupRoleOptions)
    .withServerStorageOptions(serverStorageOptions)
    .withServerRocksStorageOptions(serverRocksStorageOptions)
    .withZookeeperOptions(zookeeperOptions)
    .withPackageTransmissionOptions(packageTransmissionOptions)
    .withBookkeeperOptions(bookkeeperOptions)
    .withTracingOptions(tracingOptions)
    .build()

  server.start()

}


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

package com.bwsw.tstreamstransactionserver.options

import com.bwsw.tstreamstransactionserver.netty.client.Client
import com.bwsw.tstreamstransactionserver.options.ClientOptions._
import com.bwsw.tstreamstransactionserver.options.CommonOptions.ZookeeperOptions
import org.apache.curator.framework.CuratorFramework


class ClientBuilder private(authOpts: AuthOptions,
                            zookeeperOpts: ZookeeperOptions,
                            connectionOpts: ConnectionOptions,
                            curatorOpt: Option[CuratorFramework]
                           )
{
  private val authOptions = authOpts
  private val zookeeperOptions = zookeeperOpts
  private val connectionOptions = connectionOpts
  private val curator: Option[CuratorFramework] = curatorOpt

  def this() = this(AuthOptions(), ZookeeperOptions(), ConnectionOptions(), None)

  def withAuthOptions(authOptions: AuthOptions) = new ClientBuilder(authOptions, zookeeperOptions, connectionOptions, curator)

  def withZookeeperOptions(zookeeperOptions: ZookeeperOptions) = new ClientBuilder(authOptions, zookeeperOptions, connectionOptions, curator)

  def withCuratorConnection(curator: CuratorFramework) = new ClientBuilder(authOptions, zookeeperOptions, connectionOptions, Some(curator))

  def withConnectionOptions(clientOptions: ConnectionOptions) = new ClientBuilder(authOptions, zookeeperOptions, clientOptions, curator)

  def build() = new Client(connectionOptions, authOptions, zookeeperOptions, curator)

  def getConnectionOptions = connectionOptions.copy()

  def getZookeeperOptions = zookeeperOptions.copy()

  def getAuthOptions = authOptions.copy()
}
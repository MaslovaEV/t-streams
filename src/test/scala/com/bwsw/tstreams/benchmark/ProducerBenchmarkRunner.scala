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

package com.bwsw.tstreams.benchmark

/**
  * Performs [[ProducerBenchmark]].
  *
  * Arguments:
  * -a, --address - ZooKeeper address;
  * -t, --token - authentication token;
  * -p, --prefix - path to master node in ZooKeeper;
  * --cancel - if set, each transaction will be cancelled,
  * otherwise a checkpoint will be performed for each transaction;
  * --stream - stream name (test by default);
  * --partitions - amount of partitions on stream (1 by default);
  * --iterations - amount of measurements (100000 by default);
  * --data-size - size of data sent in each transaction (100 by default).
  *
  * @author Pavel Tomskikh
  */
object ProducerBenchmarkRunner extends BenchmarkRunner {

  override def runBenchmark(benchmark: Benchmark, config: BenchmarkConfig): ProducerBenchmark.Result =
    benchmark.testProducer(
      config.iterations(),
      dataSize = config.dataSize(),
      cancelEachTransaction = config.cancel())
}

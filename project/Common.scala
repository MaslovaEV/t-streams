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

import Publish._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.PathList

object Common {

  val assemblyStrategySettings = Seq(assemblyMergeStrategy in assembly := {
    case PathList("org", "slf4j", "impl", xs@_*) => MergeStrategy.discard
    case PathList("io", "netty", xs@_*) => MergeStrategy.first
    case PathList("org", "slf4j", xs@_*) => MergeStrategy.first
    case PathList("org", "scalatest", xs@_*) => MergeStrategy.discard
    case PathList("org", "scalamock", xs@_*) => MergeStrategy.discard
    case "log4j.properties" => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  })

  val projectSettings =
    Dependencies.Common ++
    Dependencies.`BW-SW` ++ Seq(
      scalacOptions ++= Seq(
        "-deprecation", "-feature"
      ),

      javacOptions ++= Seq(
        "-Dsun.net.maxDatagramSockets=1000"
      ),

      resolvers ++= Seq("Sonatype OSS" at "https://oss.sonatype.org/service/local/staging/deploy/maven2",
        "Sonatype OSS snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
        "Twitter Repo" at "https://maven.twttr.com",
        "Oracle Maven2 Repo" at "http://download.oracle.com/maven"),

      parallelExecution in ThisBuild := false, //tests property
      fork := true

    ) ++ assemblyStrategySettings ++ publishSettings
}

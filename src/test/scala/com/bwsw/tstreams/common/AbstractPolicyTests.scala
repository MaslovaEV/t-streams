package com.bwsw.tstreams.common

import org.scalatest.{FlatSpec, Matchers}

/**
  * Created by Ivan A. Kudryavtsev on 11.06.17.
  */
class AbstractPolicyTests extends FlatSpec with Matchers {
  val PARTITION_COUNT = 3

  class TestAbstractPartitionIterationPolicy(count: Int, set: Set[Int]) extends AbstractPartitionIterationPolicy(count, set) {
    override def getNextPartition: Int = 0
  }

  it should "handle proper partition set correctly" in {
    val partitions = Set(0,1,2)
    new TestAbstractPartitionIterationPolicy(PARTITION_COUNT, partitions)
  }

  it should "handle improper partition set correctly" in {
    val partitions = Set(0,1,4)
    intercept[IllegalArgumentException] {
      new TestAbstractPartitionIterationPolicy(PARTITION_COUNT, partitions)
    }
  }

  it should "handle correctly empty set" in {
    val partitions = Set.empty[Int]
    intercept[IllegalArgumentException] {
      new TestAbstractPartitionIterationPolicy(PARTITION_COUNT, partitions)
    }
  }

  it should "handle getCurrentPartition correctly" in {
    val partitions = Set(0,1,2)
    val p = new TestAbstractPartitionIterationPolicy(PARTITION_COUNT, partitions)
    p.getCurrentPartition shouldBe 0
  }

  it should "handle startNewRound correctly" in {
    val partitions = Set(0,1,2)
    val p = new TestAbstractPartitionIterationPolicy(PARTITION_COUNT, partitions)
    p.startNewRound()
    p.getCurrentPartition shouldBe 0
  }
}

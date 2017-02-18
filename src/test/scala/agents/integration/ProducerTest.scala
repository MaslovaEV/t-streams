package agents.integration

import com.bwsw.tstreams.agents.producer._
import com.bwsw.tstreams.env.ConfigurationOptions
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import testutils._


class ProducerTest extends FlatSpec with Matchers with BeforeAndAfterAll with TestUtils {

  System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
  System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG")
  System.setProperty("DEBUG", "true")
  System.setProperty("log4j.rootLogger", "DEBUG, STDOUT")
  System.setProperty("log4j.appender.STDOUT", "org.apache.log4j.ConsoleAppender")
  System.setProperty("log4j.appender.STDOUT.layout", "org.apache.log4j.PatternLayout")
  System.setProperty("log4j.appender.STDOUT.layout.ConversionPattern", "%5p [%t] (%F:%L) – %m%n")

  // keep it greater than 3
  val ALL_PARTITIONS = 10

  f.setProperty(ConfigurationOptions.Stream.NAME, "test_stream").
    setProperty(ConfigurationOptions.Stream.PARTITIONS, ALL_PARTITIONS).
    setProperty(ConfigurationOptions.Stream.TTL, 60 * 10).
    setProperty(ConfigurationOptions.Coordination.CONNECTION_TIMEOUT, 7).
    setProperty(ConfigurationOptions.Coordination.TTL, 7).
    setProperty(ConfigurationOptions.Producer.TRANSPORT_TIMEOUT, 5).
    setProperty(ConfigurationOptions.Producer.Transaction.TTL, 6).
    setProperty(ConfigurationOptions.Producer.Transaction.KEEP_ALIVE, 2).
    setProperty(ConfigurationOptions.Consumer.TRANSACTION_PRELOAD, 10).
    setProperty(ConfigurationOptions.Consumer.DATA_PRELOAD, 10)

  val producer = f.getProducer[String](
    name = "test_producer",
    transactionGenerator = LocalGeneratorCreator.getGen(),
    converter = stringToArrayByteConverter,
    partitions = (0 until ALL_PARTITIONS).toSet)

  "Producer.isMeAMasterOfPartition" should "return true" in {
    (0 until ALL_PARTITIONS).foreach(p => producer.isMasterOfPartition(p) shouldBe true)
  }

  "Producer.isMeAMasterOfPartition" should "return false" in {
    (ALL_PARTITIONS until ALL_PARTITIONS + 1).foreach(p => producer.isMasterOfPartition(p) shouldBe false)
  }

  "BasicProducer.newTransaction()" should "return BasicProducerTransaction instance" in {
    val transaction = producer.newTransaction(NewTransactionProducerPolicy.ErrorIfOpened)
    transaction.checkpoint()
    transaction.isInstanceOf[ProducerTransaction[_]] shouldEqual true
  }

  "BasicProducer.newTransaction(ProducerPolicies.errorIfOpen)" should "throw exception if previous transaction was not closed" in {
    val transaction1 = producer.newTransaction(NewTransactionProducerPolicy.CheckpointIfOpened, 2)
    intercept[IllegalStateException] {
      producer.newTransaction(NewTransactionProducerPolicy.ErrorIfOpened, 2)
    }
    transaction1.checkpoint()
  }

  "BasicProducer.newTransaction(checkpointIfOpen)" should "not throw exception if previous transaction was not closed" in {
    producer.newTransaction(NewTransactionProducerPolicy.CheckpointIfOpened, 2)
    val transaction2 = producer.newTransaction(NewTransactionProducerPolicy.CheckpointIfOpened, 2)
    transaction2.checkpoint()
  }

  "BasicProducer.getTransaction()" should "return transaction reference if it was created or None" in {
    val transaction = producer.newTransaction(NewTransactionProducerPolicy.CheckpointIfOpened, 1)
    val transactionRef = producer.getOpenedTransactionForPartition(1)
    transaction.checkpoint()
    val checkVal = transactionRef.get == transaction
    checkVal shouldEqual true
  }

  override def afterAll(): Unit = {
    producer.stop()
    onAfterAll()
  }
}

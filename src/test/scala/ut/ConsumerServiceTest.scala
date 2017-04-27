package ut

import com.bwsw.tstreamstransactionserver.netty.server.consumerService.{ConsumerTransactionKey, ConsumerTransactionValue}
import org.scalatest.{FlatSpec, Matchers}

class ConsumerServiceTest extends FlatSpec with Matchers {
  "Key" should "be serialized/deserialized" in {
    val key = ConsumerTransactionKey("testCheckpoint", 1L, 5)
    ConsumerTransactionKey.entryToObject(key.toDatabaseEntry) shouldBe key
    ConsumerTransactionKey.fromByteArray(key.toByteArray) shouldBe key
  }

  it should "be serialized/deserialized with negative stream" in {
    val key = ConsumerTransactionKey("testCheckpoint", -1L, 5)
    ConsumerTransactionKey.entryToObject(key.toDatabaseEntry) shouldBe key
    ConsumerTransactionKey.fromByteArray(key.toByteArray) shouldBe key
  }

  it should "be serialized/deserialized with negative partition" in {
    val key = ConsumerTransactionKey("testCheckpoint", -1L, -5)
    ConsumerTransactionKey.entryToObject(key.toDatabaseEntry) shouldBe key
    ConsumerTransactionKey.fromByteArray(key.toByteArray) shouldBe key
  }

  "ConsumerTransaction" should "be serialized/deserialized" in {
    val consumerTranasction = ConsumerTransactionValue(1L, Long.MaxValue)
    ConsumerTransactionValue.entryToObject(consumerTranasction.toDatabaseEntry) shouldBe consumerTranasction
    ConsumerTransactionValue.fromByteArray(consumerTranasction.toByteArray) shouldBe consumerTranasction
  }

  it should "be serialized/deserialized with negative transaction" in {
    val consumerTranasction = ConsumerTransactionValue(1L, Long.MaxValue)
    ConsumerTransactionValue.entryToObject(consumerTranasction.toDatabaseEntry) shouldBe consumerTranasction
    ConsumerTransactionValue.fromByteArray(consumerTranasction.toByteArray) shouldBe consumerTranasction
  }

}




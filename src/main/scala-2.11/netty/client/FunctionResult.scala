package netty.client

import java.nio.ByteBuffer

object FunctionResult {

  trait Result

  class BoolResult(val bool: Boolean) extends Result

  class SeqByteBufferResult(val bytes: Seq[ByteBuffer]) extends Result

  class LongResult(val long: Long) extends Result

  class IntResult(val int: Int) extends Result

  class SeqTransactionsResult(val txns: Seq[transactionService.rpc.Transaction]) extends Result

  class StreamResult(val stream: transactionService.rpc.Stream) extends Result

  class TokenInvalidExceptionResult(val exception: transactionService.rpc.TokenInvalidException) extends IllegalArgumentException(exception.message)
    with Result

}
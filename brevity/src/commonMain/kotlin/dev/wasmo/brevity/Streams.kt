package dev.wasmo.brevity

expect fun <T, AT> StreamPair(): StreamPair<T, AT>

interface StreamPair<T, AT> {
  val sendStream: SendStream<T, AT>
  val receiveStream: ReceiveStream<T, AT>
}

abstract class SendStream<T, AT> : Resource() {
  /** Send a single element and return. */
  abstract suspend fun send(value: T)

  /** Write [count] elements. */
  abstract suspend fun send(source: AT, offset: Int, count: Int)

  /** Cancel any in-flight sends, causing them to fail. */
  abstract fun cancel()
}

abstract class ReceiveStream<T, AT> : Resource() {
  /** Wait for a single element and return it. */
  abstract suspend fun receive(): T

  /**
   * Receive at least one, and up to [count] elements and return the number of elements received.
   * Returns -1 if the stream is exhausted.
   */
  abstract suspend fun receive(destination: AT, offset: Int, count: Int): Int

  /** Cancel any in-flight sends, causing them to fail. */
  abstract fun cancel()
}

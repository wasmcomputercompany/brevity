package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Stream
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import wit.wasmo.testing.Streams

internal class RealReceiverTester(
  private val receiver: Stream.Receiver<String>,
) : Streams.ReceiverTester() {
  private var latestReceive: Deferred<*>? = null

  override fun isSenderClosed() = receiver.senderClosed

  override suspend fun receive(count: Int): List<String> {
    return coroutineScope {
      val deferredReceive = async {
        receiver.receive(count)
      }
      latestReceive = deferredReceive
      deferredReceive.await()
    }
  }

  override suspend fun cancelReceive() {
    latestReceive?.cancel()
  }

  override fun closeReceiver() {
    receiver.close()
  }
}

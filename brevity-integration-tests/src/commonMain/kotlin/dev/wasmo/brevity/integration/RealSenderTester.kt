package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Stream
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import wit.wasmo.testing.Streams

internal class RealSenderTester(
  private val sender: Stream.Sender<String>,
) : Streams.SenderTester() {
  private var latestSend: Deferred<*>? = null

  override fun isReceiverClosed() = sender.receiverClosed

  override suspend fun sendSome(values: List<String>): Int {
    return coroutineScope {
      val deferredSend = async {
        sender.sendSome(values)
      }
      latestSend = deferredSend
      deferredSend.await()
    }
  }

  override suspend fun cancelSend() {
    latestSend?.cancel()
  }

  override fun closeSender() {
    sender.close()
  }
}

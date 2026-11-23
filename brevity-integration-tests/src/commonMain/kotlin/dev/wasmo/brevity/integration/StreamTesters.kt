package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Result
import dev.wasmo.brevity.Stream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import wit.wasmo.testing.Streams
import wit.wasmo.testing.Streams.ReceiverTester
import wit.wasmo.testing.Streams.SenderTester

internal class RealSenderTester(
  private val sender: Stream.Sender<String>,
) : SenderTester() {
  private var latestSend: Deferred<*>? = null

  override fun isReceiverClosed() = sender.receiverClosed

  override suspend fun sendSomeOrFail(values: List<String>): Result<Int, Streams.Failure> {
    return try {
      coroutineScope {
        val deferredSend = async {
          sender.sendSome(values)
        }
        latestSend = deferredSend
        Result.Ok(deferredSend.await())
      }
    } catch (e: Throwable) {
      return e.toResultOrNull() ?: throw e
    }
  }

  override suspend fun cancelSend() {
    latestSend?.cancel()
  }

  override fun closeOrFail(): Result<*, Streams.Failure> {
    try {
      sender.close()
      return Result.Ok(Unit)
    } catch (e: Throwable) {
      return e.toResultOrNull<Unit>() ?: throw e
    }
  }
}

internal class RealReceiverTester(
  private val receiver: Stream.Receiver<String>,
) : ReceiverTester() {
  private var latestReceive: Deferred<*>? = null

  override fun isSenderClosed() = receiver.senderClosed

  override suspend fun receiveOrFail(count: Int): Result<List<String>, Streams.Failure> {
    return try {
      coroutineScope {
        val deferredReceive = async {
          receiver.receive(count)
        }
        latestReceive = deferredReceive
        Result.Ok(deferredReceive.await())
      }
    } catch (e: Throwable) {
      e.toResultOrNull() ?: throw e
    }
  }

  override suspend fun cancelReceive() {
    latestReceive?.cancel()
  }

  override fun closeOrFail(): Result<*, Streams.Failure> {
    try {
      receiver.close()
      return Result.Ok(Unit)
    } catch (e: Throwable) {
      return e.toResultOrNull<Unit>() ?: throw e
    }
  }
}

private fun <T> Throwable.toResultOrNull(): Result<T, Streams.Failure>? {
  return when (this) {
    is CancellationException -> Result.Error(Streams.Failure.Canceled)
    is ConcurrentModificationException -> Result.Error(Streams.Failure.ConcurrentModification)
    is IllegalStateException -> Result.Error(Streams.Failure.IllegalState(message))
    else -> null
  }
}

private fun <T> Result<T, Streams.Failure>.getOrThrow(): T {
  when (this) {
    is Result.Error -> when (val error = this.value) {
      Streams.Failure.Canceled -> throw CancellationException()
      Streams.Failure.ConcurrentModification -> throw ConcurrentModificationException()
      is Streams.Failure.IllegalState -> throw IllegalStateException(error.value)
    }

    is Result.Ok -> return this.value
  }
}

internal suspend fun ReceiverTester.receive(count: Int): List<String> {
  return coroutineScope {
    receiveOrFail(count).getOrThrow()
  }
}

internal suspend fun SenderTester.sendSome(values: List<String>): Int {
  return coroutineScope {
    sendSomeOrFail(values).getOrThrow()
  }
}

internal fun ReceiverTester.closeReceiver() = closeOrFail().getOrThrow()

internal fun SenderTester.closeSender() = closeOrFail().getOrThrow()

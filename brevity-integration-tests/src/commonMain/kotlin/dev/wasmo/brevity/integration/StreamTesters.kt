package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Result
import dev.wasmo.brevity.Stream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import wit.wasmo.testing.Streams
import wit.wasmo.testing.Streams.ReceiverTester
import wit.wasmo.testing.Streams.SenderTester

internal class RealSenderTester(
  private val sender: Stream.Sender<String>,
) : SenderTester() {
  private var currentSendJob: Job? = null

  override fun isReceiverClosed() = sender.receiverClosed

  override suspend fun sendSomeOrFail(values: List<String>): Result<Int, Streams.Failure> {
    currentSendJob = currentCoroutineContext()[Job]
    return try {
      coroutineScope {
        Result.Ok(sender.sendSome(values))
      }
    } catch (e: Throwable) {
      return e.toResultOrNull() ?: throw e
    } finally {
      currentSendJob = null
    }
  }

  override suspend fun cancelSend() {
    currentSendJob?.cancel()
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
  private var currentReceiveJob: Job? = null

  override fun isSenderClosed() = receiver.senderClosed

  override suspend fun receiveOrFail(count: Int): Result<List<String>, Streams.Failure> {
    currentReceiveJob = currentCoroutineContext()[Job]
    return try {
      coroutineScope {
        Result.Ok(receiver.receive(count))
      }
    } catch (e: Throwable) {
      e.toResultOrNull() ?: throw e
    } finally {
      currentReceiveJob = null
    }
  }

  override suspend fun cancelReceive() {
    currentReceiveJob?.cancel()
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

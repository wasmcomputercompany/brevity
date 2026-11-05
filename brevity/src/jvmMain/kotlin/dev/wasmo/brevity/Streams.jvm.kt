package dev.wasmo.brevity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.selects.SelectBuilder

@OptIn(BrevityInternalApi::class)
actual fun <T, AT> StreamPair(): StreamPair<T, AT> = HostStreamPair()

/**
 * The host side of a stream pair is where the interesting concurrency happens.
 */
@BrevityInternalApi
class HostStreamPair<T, AT> : StreamPair<T, AT> {
  override val sendStream = HostSendStream()
  override val receiveStream = HostReceiveStream()

  private val state = MutableStateFlow(State.Idle)

  sealed class State {
    object Idle : State()
  }

  inner class HostSendStream : SendStream<T, AT>(), Waitable {
    override val index: Int
      get() = rep

    override suspend fun send(value: T) {
      error("TODO")
    }

    override suspend fun send(source: AT, offset: Int, count: Int) {
      TODO("Not yet implemented")
    }

    override fun cancel() {
      // TODO: set the waitable state to canceled
      // TODO: unblock
    }

    override fun poll(): Event {
      TODO("Not yet implemented")
    }

    override fun SelectBuilder<Event>.onEvent() {
      TODO("Not yet implemented")
    }
  }

  inner class HostReceiveStream : ReceiveStream<T, AT>(), Waitable {
    override val index: Int
      get() = rep

    override suspend fun receive(): T {
      // TODO: _something_ smart
      error("TODO")
    }

    override suspend fun receive(
      destination: AT,
      offset: Int,
      count: Int,
    ): Int {
      TODO("Not yet implemented")
    }

    override fun cancel() {
      // TODO: set the waitable state to canceled
      // TODO: unblock
    }

    override fun poll(): Event {
      TODO("Not yet implemented")
    }

    override fun SelectBuilder<Event>.onEvent() {
      TODO("Not yet implemented")
    }
  }
}

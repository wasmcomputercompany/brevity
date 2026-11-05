package dev.wasmo.brevity

import kotlinx.coroutines.selects.select

@BrevityInternalApi
class HostWaitableSet(
  val bridge: HostBridge,
) : WaitableSet() {
  val waitableIndexes = ArrayDeque<Int>()

  override fun join(waitableIndex: Int) {
    waitableIndexes += waitableIndex
  }

  override fun poll(): Event {
    for (waitableIndex in waitableIndexes) {
      val waitable = bridge.waitables[waitableIndex] ?: error("no waitable: $waitableIndex")
      val event = waitable.poll()
      if (event != Event.None) return event
    }
    return Event.None
  }

  override fun wait(): Event {
    error("not implemented")
  }

  /** Returns an arbitrary waitable that's ready, suspending if necessary. */
  suspend fun waitForEvent(): Event {
    return select {
      for (waitableIndex in waitableIndexes) {
        val waitable = bridge.waitables[waitableIndex] ?: error("no waitable: $waitableIndex")
        with(waitable) {
          onEvent()
        }
      }
    }
  }
}

package dev.wasmo.brevity

import kotlinx.coroutines.selects.select

@BrevityInternalApi
class WaitableSet(
  val bridge: HostBridge,
) : Resource() {
  val waitables = ArrayDeque<Waitable>()

  fun poll(): Event {
    for (waitable in waitables) {
      val event = waitable.poll()
      if (event != Event.None) return event
    }
    return Event.None
  }

  fun wait(): Event {
    error("not implemented")
  }

  /** Returns an arbitrary waitable that's ready, suspending if necessary. */
  suspend fun waitForEvent(): Event {
    return select {
      for (waitable in waitables) {
        waitable.select(this@select)
      }
    }
  }
}

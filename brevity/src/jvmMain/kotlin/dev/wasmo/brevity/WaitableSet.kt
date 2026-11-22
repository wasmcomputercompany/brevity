package dev.wasmo.brevity

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@BrevityInternalApi
class WaitableSet : Resource() {
  val waitables = ArrayDeque<Waitable>()

  /** If this set is currently waiting, it is waiting on this continuation. */
  private var continuation: Continuation<Waitable>? = null

  fun poll(): Event {
    for (waitable in waitables) {
      val event = waitable.take()
      if (event != Event.None) return event
    }
    return Event.None
  }

  fun wait(): Event {
    // Yikes, here we want to execute coroutines until one of them emits an event. But the hazard
    // is we are concurrency-constrained.
    error("not implemented")
  }

  /** Returns an arbitrary waitable that's ready, suspending if necessary. */
  suspend fun waitForEvent(): Event {
    check(continuation == null)

    val event = poll()
    if (event != Event.None) return event

    val waitable = suspendCancellableCoroutine { continuation ->
      this.continuation = continuation
    }
    return waitable.take()
  }

  /** Returns true if the waitable was delivered. */
  fun resume(waitable: Waitable): Boolean {
    val continuation = this.continuation ?: return false
    this.continuation = null
    continuation.resume(waitable)
    return true
  }
}

package dev.wasmo.brevity

abstract class Waitable : Resource() {
  @BrevityInternalApi
  var waitableSet: WaitableSet? = null

  @BrevityInternalApi
  private var pendingEvent: PendingEvent = PendingEvent.None

  /** If a waitable set is already waiting, resume it, so it may take the pending event. */
  @BrevityInternalApi
  internal fun enqueueEvent(pendingEvent: PendingEvent) {
    val waitableSet = this.waitableSet
    this.pendingEvent = pendingEvent
    waitableSet?.resume(this)
  }

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-waitablejoin
   */
  @BrevityInternalApi
  fun join(waitableSet: WaitableSet?) {
    // TODO: interrupt the waitable set if it's busy? crash?

    val oldWaitableSet = this.waitableSet
    if (oldWaitableSet != null) {
      oldWaitableSet.waitables -= this
    }

    this.waitableSet = waitableSet
    if (waitableSet != null) {
      waitableSet.waitables += this
    }
  }

  /** This always returns an event, but the event may be [Event.None]. */
  @BrevityInternalApi
  fun take(): Event {
    val pendingEvent = this.pendingEvent
    this.pendingEvent = PendingEvent.None
    return pendingEvent.take()
  }
}

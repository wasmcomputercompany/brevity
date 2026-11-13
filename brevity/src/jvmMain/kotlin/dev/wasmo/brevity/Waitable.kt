package dev.wasmo.brevity

abstract class Waitable : Resource() {
  @BrevityInternalApi
  var waitableSet: WaitableSet? = null

  @BrevityInternalApi
  private var pendingEvent: PendingEvent = PendingEvent.None

  /**
   * If a waitable set is already waiting, immediately deliver the event. Otherwise, store it in
   * [pendingEvent] so it's available for a forthcoming call to [take].
   */
  @BrevityInternalApi
  internal fun enqueueEvent(pendingEvent: PendingEvent) {
    val waitableSet = this.waitableSet
    if (waitableSet?.resume(pendingEvent) == true) return
    this.pendingEvent = pendingEvent
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

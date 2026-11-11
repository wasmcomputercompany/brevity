package dev.wasmo.brevity

import kotlinx.coroutines.selects.SelectBuilder

abstract class Waitable : Resource() {
  @BrevityInternalApi
  var waitableSet: WaitableSet? = null

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

  @BrevityInternalApi
  abstract fun poll(): Event

  @BrevityInternalApi
  abstract fun select(selectBuilder: SelectBuilder<Event>)
}

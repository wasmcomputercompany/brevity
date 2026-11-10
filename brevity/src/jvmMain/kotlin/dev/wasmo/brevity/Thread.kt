package dev.wasmo.brevity

@BrevityInternalApi
class Thread internal constructor(
  val bridge: HostBridge,
  val task: Task,
) {
  val context = intArrayOf(
    bridge.initialStackPointer,
    bridge.initialTlsBase,
  )
  var taskResult: Any? = null

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-contextget
   */
  fun contextGet(index: Int): Int {
    return context[index]
  }

  /**
   * https://github.com/WebAssembly/component-model/blob/main/design/mvp/Explainer.md#-contextset
   */
  fun contextSet(index: Int, value: Int) {
    context[index] = value
  }

  fun taskReturn(value: Any? = Unit) {
    this.taskResult = value
  }

  inline fun <T> execute(block: () -> T): T {
    check(bridge.currentThread == bridge.defaultThread)
    bridge.currentThread = this
    try {
      return block()
    } finally {
      bridge.currentThread = bridge.defaultThread
    }
  }
}

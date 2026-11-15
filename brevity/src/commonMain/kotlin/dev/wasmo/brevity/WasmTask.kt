package dev.wasmo.brevity

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope

/**
 * This is available in the [CoroutineContext] for suspend functions executed in WebAssembly, and
 * also suspend functions executed on the host by WebAssembly.
 *
 * Use [scope] to launch tasks that are associated to the current function.
 */
interface WasmTask : CoroutineContext.Element {
  override val key: CoroutineContext.Key<*>
    get() = Key

  val scope: CoroutineScope

  companion object Key : CoroutineContext.Key<WasmTask>
}

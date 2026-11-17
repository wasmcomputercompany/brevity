package dev.wasmo.brevity

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext

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

/**
 * Returns the currently executing task, or null if the current coroutine context doesn't have an
 * executing task.
 */
suspend fun currentTask(): WasmTask? = currentCoroutineContext()[WasmTask.Key]

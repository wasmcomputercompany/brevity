package dev.wasmo.brevity

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope

@BrevityInternalApi
class RootTask<T>(
  val name: String,
  coroutineContext: CoroutineContext,
) : WasmTask {
  override val scope: CoroutineScope = CoroutineScope(coroutineContext + CoroutineName(name) + this)
  lateinit var continuation: CancellableContinuation<T>

  fun cancelIfIncomplete() {
    continuation.cancel()
  }

  fun complete(value: Any?) {
    continuation.resume(value as T)
  }
}

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
  val continuation: CancellableContinuation<T>,
) : WasmTask {
  override val scope: CoroutineScope = CoroutineScope(coroutineContext + CoroutineName(name) + this)

  fun cancelIfIncomplete() {
    continuation.cancel()
  }

  fun complete(value: Any?) {
    continuation.resume(value as T)
  }
}

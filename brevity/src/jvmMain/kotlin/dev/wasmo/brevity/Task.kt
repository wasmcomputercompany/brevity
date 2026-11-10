package dev.wasmo.brevity

import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation

@BrevityInternalApi
class Task<T>(
  val continuation: CancellableContinuation<T>,
) {
  fun cancel() {
    continuation.cancel()
  }

  fun complete(value: Any?) {
    continuation.resume(value as T)
  }
}

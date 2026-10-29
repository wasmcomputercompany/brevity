package dev.wasmo.brevity

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler

@BrevityInternalApi
internal object BrevityCoroutineExceptionHandler : CoroutineExceptionHandler {
  override val key: CoroutineContext.Key<*>
    get() = CoroutineExceptionHandler.Key

  override fun handleException(
    context: CoroutineContext,
    exception: Throwable,
  ) {
    corePlatform.handleException(context, exception)
  }
}

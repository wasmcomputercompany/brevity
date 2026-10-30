package dev.wasmo.brevity

import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

@BrevityInternalApi
interface CorePlatform {
  suspend fun delay(duration: Duration)

  fun handleException(context: CoroutineContext, exception: Throwable)
}

@BrevityInternalApi
lateinit var corePlatform: CorePlatform

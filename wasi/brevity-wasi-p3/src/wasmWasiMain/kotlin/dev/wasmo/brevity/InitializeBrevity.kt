@file:OptIn(BrevityInternalApi::class, ExperimentalStdlibApi::class)

package dev.wasmo.brevity

import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import wit.wasi.cli.v0_3_0.Imports
import wit.wasi.cli.v0_3_0.host
import wit.wasi.clocks.v0_3_0.Duration as WasiDuration

@EagerInitialization
private val initializeBrevity = run {
  corePlatform = WasiP3CorePlatform
}

@BrevityInternalApi
private object WasiP3CorePlatform : CorePlatform {
  override suspend fun delay(duration: Duration) {
    val nanoseconds = duration.inWholeNanoseconds
    if (nanoseconds <= 0L) return
    Imports.host.monotonicClock.waitFor(WasiDuration(nanoseconds.toULong()))
  }

  override fun handleException(
    context: CoroutineContext,
    exception: Throwable,
  ) {
    // TODO: print to Imports.host.stdout
    exception.printStackTrace()
  }
}

@file:OptIn(BrevityInternalApi::class, ExperimentalStdlibApi::class)

package dev.wasmo.brevity

import kotlin.time.Duration

@EagerInitialization
private val initializeTimeMachine = run {
  GuestBridge.brevityDispatcher.offerTimeMachine(WasiP2TimeMachine)
}

private object WasiP2TimeMachine : BrevityDispatcher.TimeMachine {
  override val wasiVersion: Int
    get() = 2

  override suspend fun delay(duration: Duration) {
    // TODO
  }
}

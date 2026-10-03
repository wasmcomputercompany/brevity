@file:OptIn(BrevityInternalApi::class, ExperimentalStdlibApi::class)

package dev.wasmo.brevity

import kotlin.time.Duration

@EagerInitialization
private val initializeTimeMachine = run {
  GuestBridge.brevityDispatcher.offerTimeMachine(WasiP3TimeMachine)
}

private object WasiP3TimeMachine : BrevityDispatcher.TimeMachine {
  override val wasiVersion: Int
    get() = 3

  override suspend fun delay(duration: Duration) {
    // TODO
  }
}

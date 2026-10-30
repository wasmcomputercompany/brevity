package dev.wasmo.brevity.wasi.p3

import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.delay
import wit.wasi.clocks.v0_3_1.Duration
import wit.wasi.clocks.v0_3_1.MonotonicClock

internal class RealMonotonicClock : MonotonicClock {
  override fun now(): MonotonicClock.Mark {
    TODO("Not yet implemented")
  }

  override fun getResolution(): Duration {
    TODO("Not yet implemented")
  }

  override suspend fun waitUntil(when_: MonotonicClock.Mark) {
    TODO("Not yet implemented")
  }

  override suspend fun waitFor(howLong: Duration) {
    delay(howLong.value.toLong().nanoseconds)
  }
}

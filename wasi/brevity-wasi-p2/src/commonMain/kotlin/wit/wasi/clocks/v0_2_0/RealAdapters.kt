package wit.wasi.clocks.v0_2_0

import dev.wasmo.brevity.WitAdapter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant
import wit.wasi.clocks.v0_2_0.Adapters.MonotonicClockDuration
import wit.wasi.clocks.v0_2_0.Adapters.WallClockDatetime

internal object RealAdapters : Adapters {
  override val monotonicClockDuration = object : WitAdapter<MonotonicClockDuration, Duration> {
    override fun toWit(value: Duration) = MonotonicClockDuration(
      value = value.inWholeNanoseconds.toULong(),
    )

    override fun fromWit(wit: MonotonicClockDuration) = wit.value.toLong().nanoseconds
  }
  override val wallClockDatetime = object : WitAdapter<WallClockDatetime, Instant> {
    override fun toWit(value: Instant) = WallClockDatetime(
      seconds = value.epochSeconds.toULong(),
      nanoseconds = value.nanosecondsOfSecond.toUInt(),
    )

    override fun fromWit(wit: WallClockDatetime) = Instant.fromEpochSeconds(
      epochSeconds = wit.seconds.toLong(),
      nanosecondAdjustment = wit.nanoseconds.toInt(),
    )
  }
}

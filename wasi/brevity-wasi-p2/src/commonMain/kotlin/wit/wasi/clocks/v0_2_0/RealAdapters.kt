package wit.wasi.clocks.v0_2_0

import dev.wasmo.brevity.WitAdapter
import kotlin.time.Instant

internal object RealAdapters : Adapters {
  override val wallClockDatetime = object : WitAdapter<WallClock.Datetime, Instant> {
    override fun toWit(value: Instant) = WallClock.Datetime(
      seconds = value.epochSeconds.toULong(),
      nanoseconds = value.nanosecondsOfSecond.toUInt(),
    )

    override fun fromWit(wit: WallClock.Datetime) = Instant.fromEpochSeconds(
      epochSeconds = wit.seconds.toLong(),
      nanosecondAdjustment = wit.nanoseconds.toInt(),
    )
  }
}

package wit.wasi.clocks.v0_2_0

import dev.wasmo.brevity.WitAdapter
import kotlin.time.Instant
import wit.wasi.clocks.v0_2_0.Adapters.WallClockDatetime

internal object RealAdapters : Adapters {
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

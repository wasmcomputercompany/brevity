package dev.wasmo.brevity.wasi.p2

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource
import wit.wasi.clocks.v0_2_0.MonotonicClock
import wit.wasi.io.v0_2_0.Poll

/**
 * This class does a blocking `Thread.sleep()` because that's what's available without `async` in
 * WASIp2.
 */
internal class RealMonotonicClock(
  private val monotonicTimeSource: TimeSource.WithComparableMarks,
) : MonotonicClock {

  override fun now(): MonotonicClock.Instant {
    TODO("Not yet implemented")
  }

  override fun resolution(): Duration {
    TODO("Not yet implemented")
  }

  override fun subscribeInstant(when_: MonotonicClock.Instant): Poll.Pollable {
    TODO("Not yet implemented")
  }

  override fun subscribeDuration(when_: Duration): Poll.Pollable = DurationPollable(
    readyAt = monotonicTimeSource.markNow() + when_,
  )

  private class DurationPollable(
    val readyAt: ComparableTimeMark,
  ) : Poll.Pollable {
    override fun ready(): Boolean {
      val elapsedNow = readyAt.elapsedNow()
      return elapsedNow >= 0.nanoseconds
    }

    override fun block() {
      val delayNanos = -readyAt.elapsedNow().inWholeNanoseconds
      if (delayNanos > 0L) {
        val delayMillis = delayNanos / 1_000_000L
        Thread.sleep(delayMillis, (delayNanos - (delayMillis * 1_000_000L)).toInt())
      }
    }
  }
}

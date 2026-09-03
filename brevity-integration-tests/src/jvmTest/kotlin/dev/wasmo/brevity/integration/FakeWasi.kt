package dev.wasmo.brevity.integration

import kotlin.time.Clock
import okio.Buffer
import wit.wasi.v0_1.ClockId
import wit.wasi.v0_1.Errno
import wit.wasi.v0_1.Event
import wit.wasi.v0_1.STDERR
import wit.wasi.v0_1.STDOUT
import wit.wasi.v0_1.Subscription
import wit.wasi.v0_1.Wasi

class FakeWasi(
  val clock: Clock = Clock.System,
  val monotonicClock: () -> Long = { System.nanoTime() },
) : Wasi.Host {
  val stderr = Buffer()
  val stdout = Buffer()

  override fun getTime(clockId: ClockId): Long {
    return when (clockId) {
      ClockId.realtime -> {
        val now = clock.now()
        val seconds = now.epochSeconds
        val nanos = now.nanosecondsOfSecond
        seconds * 1_000_000_000L + nanos
      }

      ClockId.monotonic -> monotonicClock()
      ClockId.process_cputime_id -> error("unsupported clock: process_cputime_id")
      ClockId.thread_cputime_id -> error("unsupported clock: thread_cputime_id")
    }
  }

  fun sleep(clockId: ClockId, timeout: ULong, absTime: Boolean) {
    val duration = when {
      absTime -> {
        val now = getTime(clockId).toULong()
        if (now > timeout) now - timeout else 0UL
      }

      else -> timeout
    }

    if (duration > 0UL) {
      Thread.sleep((duration / 1_000_000UL).toLong(), (duration % 1_000_000UL).toInt())
    }
  }

  override fun poll(subscriptions: List<Subscription>): List<Event> {
    return subscriptions.mapNotNull { subscription ->
      when (subscription) {
        is Subscription.Clock -> {
          sleep(subscription.clockId, subscription.timeout, subscription.absTime)
          Event(
            userdata = subscription.userdata,
            errno = Errno.success,
            subscription = subscription,
          )
        }

        else -> null
      }
    }
  }

  override fun write(fd: Int, buffer: Buffer): Errno {
    return when (fd) {
      STDOUT -> {
        stdout.write(buffer, buffer.size)
        Errno.success
      }

      STDERR -> {
        stderr.write(buffer, buffer.size)
        Errno.success
      }

      else -> Errno.badf
    }
  }
}

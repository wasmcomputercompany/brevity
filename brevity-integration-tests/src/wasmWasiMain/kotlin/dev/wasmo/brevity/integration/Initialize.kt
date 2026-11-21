package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Stream
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wit.wasmo.testing.Calculator
import wit.wasmo.testing.Stdio
import wit.wasmo.testing.Streams
import wit.wasmo.testing.WasmoTesting
import wit.wasmo.testing.guest

/**
 * Note that [EagerInitialization] is necessary to trigger the side effect of initializing the
 * exported worlds.
 */
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
val actuallyInitialize = run {
  WasmoTesting.guest = object : WasmoTesting.Guest {
    override fun concat(a: String, b: String): String {
      return a + b
    }

    override val calculator = object : Calculator {
      override fun multiply(a: Long, b: Long) = a * b
    }

    override val stdio = object : Stdio {
      override fun printGreeting(name: String) {
        println("Hello, $name")
      }

      override fun printError(name: String) {
        val exception = Exception("boom, $name!")
        exception.printStackTrace()
      }
    }

    override fun sum(a: Long, b: Long): Long {
      return a + b
    }

    override val streams: Streams = object : Streams {
      override fun createGuestHost(): Pair<Streams.SenderTester, Stream.Receiver<String>> {
        val stream = Stream<String>()
        return RealSenderTester(stream.sender) to stream.receiver
      }

      override fun createGuestGuest(): Pair<Streams.SenderTester, Streams.ReceiverTester> {
        val stream = Stream<String>()
        return RealSenderTester(stream.sender) to RealReceiverTester(stream.receiver)
      }

      override fun createHostGuest(stream: Stream.Receiver<String>): Streams.ReceiverTester {
        return RealReceiverTester(stream)
      }
    }

    override suspend fun parallelSleep(coroutineCount: UInt, durationMillis: ULong) {
      coroutineScope {
        for (i in 0U until coroutineCount) {
          launch {
            delay(durationMillis.toLong().milliseconds)
          }
        }
      }
    }
  }
}

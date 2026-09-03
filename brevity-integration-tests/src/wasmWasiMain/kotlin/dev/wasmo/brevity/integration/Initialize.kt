package dev.wasmo.brevity.integration

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wit.wasmo.testing.Calculator
import wit.wasmo.testing.Streams
import wit.wasmo.testing.StringArgument
import wit.wasmo.testing.StringResult
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
    override fun concat(
      a: StringArgument,
      b: StringArgument,
      callback: StringResult,
    ) {
      callback.put(a.get() + b.get())
    }

    override val calculator = object : Calculator {
      override fun multiply(a: Long, b: Long) = a * b
    }

    override val streams = object : Streams {
      override fun printGreeting(name: StringArgument) {
        println("Hello, ${name.get()}")
      }

      override fun printError(name: StringArgument) {
        val exception = Exception("boom, ${name.get()}!")
        exception.printStackTrace()
      }
    }

    override fun sum(a: Long, b: Long): Long {
      return a + b
    }

    override fun inlineConcat(a: String, b: String): String {
      return a + b
    }

    override fun parallelSleep(coroutineCount: UInt, duration: ULong) {
      val coroutineScope = CoroutineScope(EmptyCoroutineContext)
      coroutineScope.launch {
        for (i in 0U until coroutineCount) {
          launch {
            delay(duration.toLong().milliseconds)
          }
        }
      }
    }
  }
}

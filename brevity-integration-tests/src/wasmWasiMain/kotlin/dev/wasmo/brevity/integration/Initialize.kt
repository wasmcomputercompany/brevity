package dev.wasmo.brevity.integration

import wit.wasi.cli.v0_2_0.Command
import wit.wasi.cli.v0_2_0.Run
import wit.wasi.cli.v0_2_0.guest
import wit.wasmo.testing.Calculator
import wit.wasmo.testing.Streams
import wit.wasmo.testing.Types
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
      a: Types.StringArgument,
      b: Types.StringArgument,
      callback: Types.StringResult,
    ) {
      callback.put(a.get() + b.get())
    }

    override val calculator = object : Calculator {
      override fun multiply(a: Long, b: Long) = a * b
    }

    override val streams = object : Streams {
      override fun printGreeting(name: Types.StringArgument) {
        println("Hello, ${name.get()}")
      }

      override fun printError(name: Types.StringArgument) {
        val exception = Exception("boom, ${name.get()}!")
        exception.printStackTrace()
      }
    }

    override fun sum(a: Long, b: Long): Long {
      return a + b
    }
  }

  Command.guest = object : Command.Guest {
    override val run: Run
      get() = TODO("Not yet implemented")
  }
}

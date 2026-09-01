package dev.wasmo.brevity.integration

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.WasmInstance
import dev.wasmo.brevity.wasi.p2.RealWasiP2Host
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import wit.wasi.cli.v0_2_12.Imports
import wit.wasi.cli.v0_2_12.World
import wit.wasi.v0_1.Wasi
import wit.wasi.v0_1.World
import wit.wasmo.testing.Types
import wit.wasmo.testing.WasmoTesting
import wit.wasmo.testing.World

class RunKotlinWasmTest {
  private val kotlinWasmPath = "build/compileSync/wasmWasi/main/developmentExecutable/kotlin/brevity-root-brevity-integration-tests.wasm".toPath()

  @Test
  fun `call function declared on world`() = runTest {
    val world = WasmoTesting.World { }
    val wasiP1 = FakeWasi()
    WasmInstance(
      path = kotlinWasmPath,
      worlds = listOf(
        Wasi.World({ wasiP1 }),
        Imports.World { RealWasiP2Host() },
        world,
      ),
    )
    val result = world.guest.sum(5L, 10L)
    assertThat(result).isEqualTo(15L)
  }

  @Test
  fun `call function declared on interface`() = runTest {
    val world = WasmoTesting.World { }
    val wasiP1 = FakeWasi()
    WasmInstance(
      path = kotlinWasmPath,
      worlds = listOf(
        Wasi.World({ wasiP1 }),
        Imports.World { RealWasiP2Host() },
        world,
      ),
    )
    val result = world.guest.calculator.multiply(5L, 10L)
    assertThat(result).isEqualTo(50L)
  }

  @Test
  fun `call concatenate`() = runTest {
    val world = WasmoTesting.World { }
    val wasiP1 = FakeWasi()
    WasmInstance(
      path = kotlinWasmPath,
      worlds = listOf(
        Wasi.World({ wasiP1 }),
        Imports.World { RealWasiP2Host() },
        world,
      ),
    )
    val a = "Hello, ".asStringArgument()
    val b = "World!".asStringArgument()

    var result: String? = null
    val callback = object : Types.StringResult {
      override fun put(value_: String) {
        result = value_
      }
    }

    world.guest.concat(a, b, callback)
    assertThat(result).isEqualTo("Hello, World!")
  }

  @Test
  fun `call inline concatenate`() = runTest {
    val world = WasmoTesting.World { }
    val wasiP1 = FakeWasi()
    WasmInstance(
      path = kotlinWasmPath,
      worlds = listOf(
        Wasi.World({ wasiP1 }),
        Imports.World { RealWasiP2Host() },
        world,
      ),
    )
    assertThat(world.guest.inlineConcat("Hello, ", "World!"))
      .isEqualTo("Hello, World!")
  }

  private fun String.asStringArgument() = object : Types.StringArgument {
    override fun get() = this@asStringArgument
  }

  @Test
  fun `call printGreeting`() = runTest {
    val world = WasmoTesting.World { }
    val wasiP1 = FakeWasi()
    WasmInstance(
      path = kotlinWasmPath,
      worlds = listOf(
        Wasi.World({ wasiP1 }),
        Imports.World { RealWasiP2Host() },
        world,
      ),
    )
    val name = "Jesse".asStringArgument()
    world.guest.streams.printGreeting(name)

    assertThat(wasiP1.stdout.readUtf8()).isEqualTo("Hello, Jesse\n")
  }

  @Test
  fun `call printError`() = runTest {
    val world = WasmoTesting.World { }
    val wasiP1 = FakeWasi()
    WasmInstance(
      path = kotlinWasmPath,
      worlds = listOf(
        Wasi.World({ wasiP1 }),
        Imports.World { RealWasiP2Host() },
        world,
      ),
    )
    val name = "Jesse".asStringArgument()
    world.guest.streams.printError(name)

    assertThat(wasiP1.stderr.readUtf8()).isEqualTo("Exception: boom, Jesse!\n\n")
  }
}

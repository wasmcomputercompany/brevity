package dev.wasmo.brevity.integration

import app.cash.burst.Burst
import assertk.assertThat
import assertk.assertions.isEqualTo
import brevity.wasi.p2.RealCommandHost
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import wit.wasi.cli.v0_2_0.Command
import wit.wasi.cli.v0_2_0.World
import wit.wasmo.testing.Types
import wit.wasmo.testing.WasmoTesting
import wit.wasmo.testing.World

@Burst
class RunKotlinWasmTest {
  @Test
  fun `call function declared on world`() = runTest {
    val world = WasmoTesting.World { Unit }
    val tester = WasmTester.Builder()
      .wasmPath(WasmSource.Kotlin.path)
      .addWorld(Command.World { guest -> RealCommandHost(guest) })
      .addWorld(world)
      .build()
    val result = world.guest.sum(5L, 10L)
    assertThat(result).isEqualTo(15L)
  }

  @Test
  fun `call function declared on interface`() = runTest {
    val world = WasmoTesting.World { Unit }
    val tester = WasmTester.Builder()
      .wasmPath(WasmSource.Kotlin.path)
      .addWorld(Command.World { guest -> RealCommandHost(guest) })
      .addWorld(world)
      .build()
    val result = world.guest.calculator.multiply(5L, 10L)
    assertThat(result).isEqualTo(50L)
  }

  @Test
  fun `call concatenate`() = runTest {
    val world = WasmoTesting.World { Unit }
    val tester = WasmTester.Builder()
      .wasmPath(WasmSource.Kotlin.path)
      .addWorld(Command.World { guest -> RealCommandHost(guest) })
      .addWorld(world)
      .build()

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

  private fun String.asStringArgument() = object : Types.StringArgument {
    override fun get() = this@asStringArgument
  }

  @Test
  fun `call printGreeting`() = runTest {
    val world = WasmoTesting.World { Unit }
    val tester = WasmTester.Builder()
      .addWorld(Command.World { guest -> RealCommandHost(guest) })
      .addWorld(world)
      .wasmPath(WasmSource.Kotlin.path)
      .build()

    val name = "Jesse".asStringArgument()
    world.guest.streams.printGreeting(name)

    assertThat(tester.wasi.stdout.readUtf8()).isEqualTo("Hello, Jesse\n")
  }

  @Test
  fun `call printError`() = runTest {
    val world = WasmoTesting.World { Unit }
    val tester = WasmTester.Builder()
      .addWorld(Command.World { guest -> RealCommandHost(guest) })
      .addWorld(world)
      .wasmPath(WasmSource.Kotlin.path)
      .build()

    val name = "Jesse".asStringArgument()
    world.guest.streams.printError(name)

    assertThat(tester.wasi.stderr.readUtf8()).isEqualTo("Exception: boom, Jesse!\n\n")
  }

  enum class WasmSource(
    val path: Path,
  ) {
    Kotlin(
      path = "build/compileSync/wasmWasi/main/developmentExecutable/kotlin/brevity-root-brevity-integration-tests.wasm".toPath(),
    ),
    Rust(
      path = "rust/target/unbundled/unbundled-module0.wasm".toPath(),
    )
  }
}

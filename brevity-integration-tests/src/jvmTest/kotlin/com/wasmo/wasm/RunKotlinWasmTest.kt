package com.wasmo.wasm

import app.cash.burst.Burst
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNullOrEmpty
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
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
      .addWorld(world)
      .build()
    val result = world.guest.calculator.multiply(5L, 10L)
    assertThat(result).isEqualTo(50L)
  }

  @Test
  fun `call kotlin concatenate`() = runTest {
    val world = WasmoTesting.World { Unit }
    val tester = WasmTester.Builder()
      .wasmPath(WasmSource.Kotlin.path)
      .addWorld(world)
      .build()

    val a = object : Types.StringArgument {
      override fun get(): String {
        return "Hello, "
      }
    }
    val b = object : Types.StringArgument {
      override fun get(): String {
        return "World!"
      }
    }
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
  fun `call printGreeting`() = runTest {
    val world = WasmoTesting.World { Unit }
    val tester = WasmTester.Builder()
      .addWorld(world)
      .wasmPath(WasmSource.Kotlin.path)
      .build()

    val nameId = tester.bridge.put("Jesse")

    val concatenate = tester.instance.export("printGreeting")
    val result = concatenate.apply(nameId.toLong())
    assertThat(result).isNullOrEmpty()

    assertThat(tester.wasi.stdout.readUtf8()).isEqualTo("Hello, Jesse\n")
  }

  @Test
  fun `call printError`() = runTest {
    val world = WasmoTesting.World { Unit }
    val tester = WasmTester.Builder()
      .addWorld(world)
      .wasmPath(WasmSource.Kotlin.path)
      .build()
    val nameId = tester.bridge.put("Jesse")

    val concatenate = tester.instance.export("printError")
    val result = concatenate.apply(nameId.toLong())
    assertThat(result).isNullOrEmpty()

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

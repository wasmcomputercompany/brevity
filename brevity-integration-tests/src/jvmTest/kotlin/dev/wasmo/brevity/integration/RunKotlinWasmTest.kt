package dev.wasmo.brevity.integration

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import wit.wasmo.testing.StringArgument
import wit.wasmo.testing.StringResult

class RunKotlinWasmTest {
  @Test
  fun `call function declared on world`() = runTest {
    val tester = BrevityIntegrationTester()
    val result = tester.world.guest.sum(5L, 10L)
    assertThat(result).isEqualTo(15L)
  }

  @Test
  fun `call function declared on interface`() = runTest {
    val tester = BrevityIntegrationTester()
    val result = tester.world.guest.calculator.multiply(5L, 10L)
    assertThat(result).isEqualTo(50L)
  }

  @Test
  fun `call concatenate`() = runTest {
    val tester = BrevityIntegrationTester()
    val a = "Hello, ".asStringArgument()
    val b = "World!".asStringArgument()

    var result: String? = null
    val callback = object : StringResult() {
      override fun put(value_: String) {
        result = value_
      }
    }

    tester.world.guest.concat(a, b, callback)
    assertThat(result).isEqualTo("Hello, World!")
  }

  @Test
  fun `call inline concatenate`() = runTest {
    val tester = BrevityIntegrationTester()
    assertThat(tester.world.guest.inlineConcat("Hello, ", "World!"))
      .isEqualTo("Hello, World!")
  }

  private fun String.asStringArgument() = object : StringArgument() {
    override fun get() = this@asStringArgument
  }

  @Test
  fun `call printGreeting`() = runTest {
    val tester = BrevityIntegrationTester()
    val name = "Jesse".asStringArgument()
    tester.world.guest.streams.printGreeting(name)

    assertThat(tester.wasiP1.stdout.readUtf8()).isEqualTo("Hello, Jesse\n")
  }

  @Test
  fun `call printError`() = runTest {
    val tester = BrevityIntegrationTester()
    val name = "Jesse".asStringArgument()
    tester.world.guest.streams.printError(name)

    assertThat(tester.wasiP1.stderr.readUtf8()).isEqualTo("kotlin.Exception: boom, Jesse!\n")
  }

  @Test
  fun `parallel sleep`() = runTest {
    val tester = BrevityIntegrationTester()
    val elapsed = measureTestTime {
      tester.world.guest.parallelSleep(30U, 100UL)
    }
    assertThat(elapsed).isEqualTo(100.milliseconds)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun TestScope.measureTestTime(block: suspend () -> Unit): Duration {
    val timeBefore = currentTime
    block()
    return (currentTime - timeBefore).milliseconds
  }
}

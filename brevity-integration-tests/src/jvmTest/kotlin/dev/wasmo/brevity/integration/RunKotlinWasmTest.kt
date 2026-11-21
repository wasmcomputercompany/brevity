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
    assertThat(tester.world.guest.concat("Hello, ", "World!"))
      .isEqualTo("Hello, World!")
  }

  @Test
  fun `call printGreeting`() = runTest {
    val tester = BrevityIntegrationTester()
    tester.world.guest.stdio.printGreeting("Jesse")

    assertThat(tester.wasiP1.stdout.readUtf8()).isEqualTo("Hello, Jesse\n")
  }

  @Test
  fun `call printError`() = runTest {
    val tester = BrevityIntegrationTester()
    tester.world.guest.stdio.printError("Jesse")

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

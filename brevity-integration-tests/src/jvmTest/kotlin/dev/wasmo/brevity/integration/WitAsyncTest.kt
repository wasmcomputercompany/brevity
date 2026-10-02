package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Ignore
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class WitAsyncTest {
  private val type = SampleType(
    id = Identifier("s64"),
    witType = "s64",
    kotlinType = "Long",
    rustType = "i64",
    values = listOf(
      SampleValue(kotlin = "0L", rust = "0"),
      SampleValue(kotlin = "5L", rust = "5"),
      SampleValue(kotlin = "kotlin.Long.MIN_VALUE", rust = "-9223372036854775808"),
      SampleValue(kotlin = "kotlin.Long.MAX_VALUE", rust = "9223372036854775807"),
    ),
  )

  @Test
  fun happyPath() = runTest {
    val test = BrevityExecutionTester(
      name = "asyncHappyPath",
      types = listOf(type),
    )

    test.execute()
  }

  @Test
  @Ignore("not working on Kotlin yet")
  fun sleep() = runTest {
    val test = BrevityExecutionTester(
      name = "asyncSleep",
      types = listOf(type),
      sleep = true,
    )

    test.execute()
  }
}

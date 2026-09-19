package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Ignore
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class WitAsyncTest {
  @Test
  @Ignore("async isn't working yet")
  fun happyPath() = runTest {
    val test = BrevityExecutionTester(
      name = "asyncHappyPath",
      rawWit = """
        |  type datetime = s64;
        |
        """.trimMargin(),
      types = listOf(
        SampleType(
          id = Identifier("datetime"),
          witType = "datetime",
          kotlinType = "BrevityTest.Datetime",
          rustType = "bindings::Datetime",
          async = true,
          values = listOf(
            SampleValue(
              kotlin = "BrevityTest.Datetime(3_600_000L)",
              rust = "3600000",
            ),
            SampleValue(
              kotlin = "BrevityTest.Datetime(7_200_000L)",
              rust = "7200000",
            ),
          ),
        ),
      ),
    )

    test.execute()
  }
}

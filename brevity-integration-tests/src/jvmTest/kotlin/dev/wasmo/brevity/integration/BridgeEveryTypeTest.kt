package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BridgeEveryTypeTest {
  @Test
  fun primitives() = runTest {
    val test = BrevityExecutionTester(
      name = "primitives",
      types = listOf(
        SampleType(
          id = Identifier("s32"),
          witType = "s32",
          kotlinType = "Int",
          rustType = "i32",
          values = listOf(
            SampleValue(kotlin = "0", rust = "0"),
            SampleValue(kotlin = "5", rust = "5"),
            SampleValue(kotlin = "-2147483648", rust = "-2147483648"),
            SampleValue(kotlin = "2147483647", rust = "2147483647"),
          ),
        ),
        SampleType(
          id = Identifier("bool"),
          witType = "bool",
          kotlinType = "Boolean",
          rustType = "bool",
          values = listOf(
            SampleValue(kotlin = "false", rust = "false"),
            SampleValue(kotlin = "true", rust = "true"),
          ),
        ),
      ),
    )

    test.execute()
  }
}

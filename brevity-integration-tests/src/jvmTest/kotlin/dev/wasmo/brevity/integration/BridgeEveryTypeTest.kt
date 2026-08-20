package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Ignore
import kotlin.test.Test
import okio.Path.Companion.toPath

/**
 * To run this test:
 *
 *  - publish to maven local with version 0.1.0 (not SNAPSHOT)
 *    - may require commenting out 'sign all publications'
 *  - install the Kotlin toolchain
 */
class BridgeEveryTypeTest {
  @Test
  @Ignore("the dependencies of this test aren't wired up yet")
  fun primitives() {
    val test = BrevityExecutionTester(
      path = "build/BridgeTypeTest/primitives".toPath(),
      types = listOf(
        SampleType(
          id = Identifier("s32"),
          witType = "s32",
          kotlinType = "Int",
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

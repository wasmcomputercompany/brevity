package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BridgeFlagsTest {
  @Test
  fun test() = runTest {
    val enumSizes = listOf(
      1, // min byte
      8, // max byte
      9, // min short
      16, // max short
      17, // min int
      32, // max int
    )
    val test = BrevityExecutionTester(
      name = "flags",
      rawWit = enumSizes.joinToString(separator = "\n") { story ->
        """
        |  flags elevator-${story}-story {
        |    ${List(story) { "floor-${it + 1}-chosen" }.joinToString(separator = ",")}
        |  }
        |""".trimMargin()
      },
      types = enumSizes.map { i ->
        SampleType(
          id = Identifier("elevator-$i-story"),
          witType = "elevator-$i-story",
          kotlinType = "BrevityTest.Elevator${i}Story",
          rustType = "bindings::Elevator${i}Story",
          values = buildList {
            // All `true`
            add(
              SampleValue(
                kotlin = "BrevityTest.Elevator${i}Story(${
                  List(i) { "true" }.joinToString(separator = ",")
                })",
                rust = List(i) { floor -> "bindings::Elevator${i}Story::FLOOR_${floor + 1}_CHOSEN" }.joinToString(
                  separator = " | ",
                ),
              ),
            )
            // All `false`.
            add(
              SampleValue(
                kotlin = "BrevityTest.Elevator${i}Story(${
                  List(i) { "false" }.joinToString(separator = ",")
                })",
                rust = "bindings::Elevator${i}Story::empty()",
              ),
            )
            if (i > 1) {
              // Even floors
              add(
                SampleValue(
                  kotlin = "BrevityTest.Elevator${i}Story(${
                    List(i) { if (it % 2 == 1) "true" else "false" }.joinToString(separator = ",")
                  })",
                  rust = List(i / 2) { floor -> "bindings::Elevator${i}Story::FLOOR_${(floor + 1) * 2}_CHOSEN" }.joinToString(
                    separator = " | ",
                  ),
                ),
              )
              // Odd floors
              add(
                SampleValue(
                  kotlin = "BrevityTest.Elevator${i}Story(${
                    List(i) { if (it % 2 == 0) "true" else "false" }.joinToString(separator = ",")
                  })",
                  rust = List((i + 1) / 2) { floor -> "bindings::Elevator${i}Story::FLOOR_${((floor + 1) * 2) - 1}_CHOSEN" }.joinToString(
                    separator = " | ",
                  ),
                ),
              )
            }
          },
        )
      },
    )

    test.execute()
  }
}

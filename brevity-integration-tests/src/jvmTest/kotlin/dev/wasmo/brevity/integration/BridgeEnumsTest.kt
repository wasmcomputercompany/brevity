package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BridgeEnumsTest {
  @Test
  fun test() = runTest {
    val test = BrevityExecutionTester(
      name = "enums",
      rawWit = """
        |  enum shaker {
        |    salt,
        |    pepper,
        |  }
        |  enum max-byte-enum {
        |    ${List(256) { "v$it" }.joinToString(separator = ",")}
        |  }
        |  enum min-short-enum {
        |    ${List(257) { "v$it" }.joinToString(separator = ",")}
        |  }
        |""".trimMargin(),
      types = listOf(
        SampleType(
          id = Identifier("shaker"),
          witType = "shaker",
          kotlinType = "BrevityTest.Shaker",
          rustType = "bindings::Shaker",
          values = listOf(
            SampleValue(kotlin = "BrevityTest.Shaker.Salt", rust = "bindings::Shaker::Salt"),
            SampleValue(kotlin = "BrevityTest.Shaker.Pepper", rust = "bindings::Shaker::Pepper"),
          ),
        ),
        SampleType(
          id = Identifier("max-byte-enum"),
          witType = "max-byte-enum",
          kotlinType = "BrevityTest.MaxByteEnum",
          rustType = "bindings::MaxByteEnum",
          values = listOf(
            SampleValue(kotlin = "BrevityTest.MaxByteEnum.V0", rust = "bindings::MaxByteEnum::V0"),
            SampleValue(kotlin = "BrevityTest.MaxByteEnum.V255", rust = "bindings::MaxByteEnum::V255"),
          ),
        ),
        SampleType(
          id = Identifier("min-short-enum"),
          witType = "min-short-enum",
          kotlinType = "BrevityTest.MinShortEnum",
          rustType = "bindings::MinShortEnum",
          values = listOf(
            SampleValue(kotlin = "BrevityTest.MinShortEnum.V0", rust = "bindings::MinShortEnum::V0"),
            SampleValue(kotlin = "BrevityTest.MinShortEnum.V255", rust = "bindings::MinShortEnum::V255"),
            SampleValue(kotlin = "BrevityTest.MinShortEnum.V256", rust = "bindings::MinShortEnum::V256"),
          ),
        ),
      ),
    )

    test.execute()
  }
}

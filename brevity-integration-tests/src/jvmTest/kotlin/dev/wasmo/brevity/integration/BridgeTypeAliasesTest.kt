package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BridgeTypeAliasesTest {
  @Test
  fun test() = runTest {
    val test = BrevityExecutionTester(
      name = "typeAliases",
      rawWit = """
        |  type email-address = string;
        |""".trimMargin(),
      types = listOf(
        SampleType(
          id = Identifier("email-address"),
          witType = "email-address",
          kotlinType = "BrevityTest.EmailAddress",
          rustType = "bindings::EmailAddress",
          values = listOf(
            SampleValue(
              kotlin = "BrevityTest.EmailAddress(\"hello@wasmo.com\")",
              rust = "\"hello@wasmo.com\"",
            ),
            SampleValue(
              kotlin = "BrevityTest.EmailAddress(\"noreply@wasmo.com\")",
              rust = "\"noreply@wasmo.com\"",
            ),
          ),
        ),
      ),
    )

    test.execute()
  }
}

package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Orientation
import kotlin.test.Ignore
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class WitImportTest {
  private val type = SampleType(
    id = Identifier("s64"),
    witType = "s64",
    kotlinType = "Long",
    rustType = "i64",
    // Async isn't implemented correctly yet on the host.
    callingMechanisms = listOf(
      CallingMechanism.Sync,
    ),
    orientations = listOf(
      Orientation.Export,
      Orientation.Import,
    ),
    values = listOf(
      SampleValue(kotlin = "0L", rust = "0"),
      SampleValue(kotlin = "5L", rust = "5"),
      SampleValue(kotlin = "kotlin.Long.MIN_VALUE", rust = "-9223372036854775808"),
      SampleValue(kotlin = "kotlin.Long.MAX_VALUE", rust = "9223372036854775807"),
    ),
  )

  @Test
  @Ignore("not working on Kotlin yet")
  fun happyPath() = runTest {
    val test = BrevityExecutionTester(
      name = "importHappyPath",
      types = listOf(type),
    )

    test.execute()
  }
}

package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class WitAdapterTest {
  @Test
  fun happyPath() = runTest {
    val test = BrevityExecutionTester(
      name = "witAdapterHappyPath",
      rawWit = """
        |  type datetime = s64;
        |
        """.trimMargin(),
      extraFiles = mapOf(
        "brevity/src/wit/brevity/testing/RealAdapters.kt".toPath() to """
          |package wit.brevity.testing
          |
          |import dev.wasmo.brevity.WitAdapter
          |import kotlin.time.Instant
          |import wit.brevity.testing.Adapters.BrevityTestDatetime
          |
          |internal object RealAdapters : Adapters {
          |  override val brevityTestDatetime = object : WitAdapter<BrevityTestDatetime, Instant> {
          |    override fun fromWit(wit: BrevityTestDatetime) =
          |      Instant.fromEpochMilliseconds(wit.value)
          |
          |    override fun toWit(value: Instant) =
          |      BrevityTestDatetime(value.toEpochMilliseconds())
          |  }
          |}
          |
          """.trimMargin(),
      ),
      types = listOf(
        SampleType(
          id = Identifier("datetime"),
          witType = "datetime",
          kotlinType = "kotlin.time.Instant",
          kotlinTypeMapping = true,
          rustType = "bindings::Datetime",
          values = listOf(
            SampleValue(
              kotlin = "kotlin.time.Instant.fromEpochSeconds(3_600L)",
              rust = "3600000",
            ),
            SampleValue(
              kotlin = "kotlin.time.Instant.fromEpochSeconds(7_200L)",
              rust = "7200000",
            ),
          ),
        ),
      ),
    )

    test.execute()
  }
}

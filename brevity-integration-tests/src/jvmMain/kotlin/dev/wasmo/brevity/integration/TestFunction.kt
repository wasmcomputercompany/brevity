package dev.wasmo.brevity.integration

import dev.wasmo.brevity.integration.PassingMechanism.PassAsParameter
import dev.wasmo.brevity.integration.PassingMechanism.PassAsReturnValue
import okio.BufferedSink

val List<SampleType>.testFunctions: List<TestFunction>
  get() = buildList {
    for (type in this@testFunctions) {
      for (callingMechanism in type.callingMechanisms) {
        if (callingMechanism is CallingMechanism.Sync) {
          add(TestFunction(PassAsParameter(), callingMechanism, type))
          add(TestFunction(PassAsParameter(16), callingMechanism, type))
        } else {
          add(TestFunction(PassAsParameter(), callingMechanism, type))
          add(TestFunction(PassAsParameter(4), callingMechanism, type))
        }
      }
    }
  }

data class TestFunction(
  val passingMechanism: PassingMechanism,
  val callingMechanism: CallingMechanism,
  val type: SampleType,
) {
  val testCases: List<TestCase>
    get() = type.values.indices.map { TestCase(this, it) }

  val witFunctionName: String
    get() = buildString {
      append(
        when (passingMechanism) {
          is PassAsParameter -> "pass-as-parameter"
          PassAsReturnValue -> "pass-as-return-value"
        },
      )
      append("-")
      append(type.id)
      if (padding > 0) {
        append("-p$padding")
      }
      if (callingMechanism is CallingMechanism.Async) {
        append("-async")
      }
    }

  val kotlinFunctionName: String
    get() = buildString {
      append(
        when (passingMechanism) {
          is PassAsParameter -> "passAsParameter"
          PassAsReturnValue -> "passAsReturnValue"
        },
      )
      append(type.idUpperCamel)
      if (padding > 0) {
        append("P$padding")
      }
      if (callingMechanism is CallingMechanism.Async) {
        append("Async")
      }
    }

  val rustFunctionName: String
    get() = buildString {
      append(
        when (passingMechanism) {
          is PassAsParameter -> "pass_as_parameter"
          PassAsReturnValue -> "pass_as_return_value"
        },
      )
      append("_")
      append(type.idLowerSnake)
      if (padding > 0) {
        append("_p$padding")
      }
      if (callingMechanism is CallingMechanism.Async) {
        append("_async")
      }
    }

  val padding: Int
    get() = (passingMechanism as? PassAsParameter)?.padding ?: 0

  override fun toString() =
    "${type.id}.$passingMechanism.$callingMechanism"

  fun BufferedSink.witDeclare() {
    val modifiers = when (callingMechanism) {
      is CallingMechanism.Async -> " async"
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |  export $witFunctionName:$modifiers func(
      |
      """.trimMargin(),
    )
    for (i in 0 until padding) {
      writeUtf8(
        """
        |    p$i: s32,
        |
        """.trimMargin(),
      )
    }
    when (passingMechanism) {
      is PassAsParameter -> {
        writeUtf8(
          """
          |    v: ${type.witType},
          |  ) -> s32;
          |
          """.trimMargin(),
        )
      }

      PassAsReturnValue -> {
        writeUtf8(
          """
          |    index: s32,
          |  ) -> ${type.witType};
          |
          """.trimMargin(),
        )
      }
    }
  }

  fun BufferedSink.rustDeclare() {
    val modifiers = when (callingMechanism) {
      is CallingMechanism.Async -> "async "
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |    ${modifiers}fn $rustFunctionName(
      |
      """.trimMargin(),
    )
    for (i in 0 until padding) {
      writeUtf8(
        """
        |        _p$i: i32,
        |
        """.trimMargin(),
      )
    }

    when (passingMechanism) {
      is PassAsParameter -> {
        writeUtf8(
          """
          |        v: ${type.rustType}
          |    ) -> i32 {
          |
          """.trimMargin(),
        )

        maybeWriteSleepRust()

        if (type.compareAsString) {
          writeUtf8(
            """
            |        let v_str = v.to_string();
            |
            """.trimMargin(),
          )
          for ((index, value) in type.values.withIndex()) {
            writeUtf8(
              """
              |        if v_str == (${value.rust}).to_string() { return $index }
              |
              """.trimMargin(),
            )
          }
        } else {
          for ((index, value) in type.values.withIndex()) {
            writeUtf8(
              """
              |        if v == ${value.rust} { return $index }
              |
              """.trimMargin(),
            )
          }
        }
        writeUtf8(
          """
          |        panic!("unexpected value")
          |    }
          |
          """.trimMargin(),
        )
      }

      PassAsReturnValue -> {
        writeUtf8(
          """
          |        index: i32
          |    ) -> ${type.rustType} {
          |
          """.trimMargin(),
        )

        maybeWriteSleepRust()

        writeUtf8(
          """
          |        match index {
          |
          """.trimMargin(),
        )
        for ((index, value) in type.values.withIndex()) {
          writeUtf8(
            """
            |            $index => (${value.rust}).to_owned(),
            |
            """.trimMargin(),
          )
        }
        writeUtf8(
          """
          |            _ => panic!("unexpected index {}", index)
          |        }
          |    }
          |
          """.trimMargin(),
        )
      }
    }
  }

  fun BufferedSink.kotlinDeclare() {
    val modifiers = when (callingMechanism) {
      is CallingMechanism.Async -> " suspend"
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |  override$modifiers fun $kotlinFunctionName(
      |
      """.trimMargin(),
    )

    val padding = (passingMechanism as? PassAsParameter)?.padding ?: 0
    for (i in 0 until padding) {
      writeUtf8(
        """
        |    p$i: Int,
        |
        """.trimMargin(),
      )
    }

    when (passingMechanism) {
      is PassAsParameter -> {
        writeUtf8(
          """
          |    v: ${type.kotlinType},
          |  ): Int {
          |
          """.trimMargin(),
        )
        maybeWriteSleepKotlin()

        if (type.compareAsString) {
          writeUtf8(
            """
            |    val v_str = v.toString()
            |
            """.trimMargin(),
          )
          for ((index, value) in type.values.withIndex()) {
            writeUtf8(
              """
              |    if (v_str == (${value.kotlin}).toString()) { return $index }
              |
              """.trimMargin(),
            )
          }
        } else {
          for ((index, value) in type.values.withIndex()) {
            if (type.kotlinEqualityMethod == null) {
              writeUtf8(
                """
                |    if (v == ${value.kotlin}) { return $index }
                |
                """.trimMargin(),
              )
            } else {
              writeUtf8(
                """
                |    if (v.${type.kotlinEqualityMethod}(${value.kotlin})) { return $index }
                |
                """.trimMargin(),
              )
            }
          }
        }
        writeUtf8(
          """
          |    return -1
          |  }
          |
          """.trimMargin(),
        )
      }

      PassAsReturnValue -> {
        writeUtf8(
          """
          |    index: Int,
          |  ): ${type.kotlinType} {
          |
          """.trimMargin(),
        )
        maybeWriteSleepKotlin()
        writeUtf8(
          """
          |    return when (index) {
          |
          """.trimMargin(),
        )
        for ((index, value) in type.values.withIndex()) {
          writeUtf8(
            """
            |      $index -> ${value.kotlin}
            |
            """.trimMargin(),
          )
        }
        writeUtf8(
          $$"""
          |      else -> error("unexpected index: $index")
          |    }
          |  }
          |
          """.trimMargin(),
        )
      }
    }
  }

  private fun BufferedSink.maybeWriteSleepKotlin() {
    if ((callingMechanism as? CallingMechanism.Async)?.sleep != true) return
    writeUtf8(
      """
      |    delay(100.milliseconds)
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.maybeWriteSleepRust() {
    if ((callingMechanism as? CallingMechanism.Async)?.sleep != true) return
    writeUtf8(
      """
      |        sleep(Duration::from_millis(4000));
      |
      """.trimMargin(),
    )
  }
}

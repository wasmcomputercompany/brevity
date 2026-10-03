package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import dev.wasmo.brevity.kotlin.generator.lowerSnakeCase
import okio.Buffer
import okio.BufferedSink

val List<SampleType>.testFunctions: List<TestFunction>
  get() = buildList {
    for (type in this@testFunctions) {
      for (orientation in type.orientations) {
        for (callingMechanism in type.callingMechanisms) {
          if (callingMechanism is CallingMechanism.Sync) {
            add(TestFunction(PassingMechanism.Parameter(), callingMechanism, orientation, type))
            add(TestFunction(PassingMechanism.Parameter(16), callingMechanism, orientation, type))
            add(TestFunction(PassingMechanism.Return, callingMechanism, orientation, type))
          } else {
            add(TestFunction(PassingMechanism.Parameter(), callingMechanism, orientation, type))
            add(TestFunction(PassingMechanism.Parameter(4), callingMechanism, orientation, type))
            add(TestFunction(PassingMechanism.Return, callingMechanism, orientation, type))
          }
        }
      }
    }
  }

data class TestFunction(
  val passingMechanism: PassingMechanism,
  val callingMechanism: CallingMechanism,
  val orientation: Orientation,
  val type: SampleType,
) {
  val testCases: List<TestCase>
    get() = type.values.indices.map { TestCase(this, it) }

  val identifier = Identifier(
    buildList {
      add(orientation.keyword)
      add(
        when (passingMechanism) {
          is PassingMechanism.Parameter -> "parameter"
          PassingMechanism.Return -> "return"
        },
      )
      add(type.id.name)
      if (padding > 0) {
        add("p$padding")
      }
      if (callingMechanism is CallingMechanism.Async) {
        add("async")
      }
    }.joinToString(separator = "-"),
  )

  val padding: Int
    get() = (passingMechanism as? PassingMechanism.Parameter)?.padding ?: 0

  private val indexToValueFunction: TestFunction
    get() = TestFunction(
      PassingMechanism.Return,
      CallingMechanism.Sync,
      Orientation.Export,
      type,
    )

  private val valueToIndexFunction: TestFunction
    get() = TestFunction(
      PassingMechanism.Parameter(),
      CallingMechanism.Sync,
      Orientation.Export,
      type,
    )

  override fun toString() = identifier.name

  fun BufferedSink.witDeclare() {
    val modifiers = when (callingMechanism) {
      is CallingMechanism.Async -> " async"
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |  ${orientation.keyword} $identifier:$modifiers func(
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
      is PassingMechanism.Parameter -> {
        writeUtf8(
          """
          |    v: ${type.witType},
          |  ) -> s32;
          |
          """.trimMargin(),
        )
      }

      PassingMechanism.Return -> {
        writeUtf8(
          """
          |    index: s32,
          |  ) -> ${type.witType};
          |
          """.trimMargin(),
        )
      }
    }
    if (orientation == Orientation.Import) {
      writeUtf8(
        """
        |  export $identifier-trampoline:$modifiers func(index: s32) -> s32;
        |
        """.trimMargin(),
      )
    }
  }


  fun BufferedSink.rustDeclareGuestFunctions() {
    when (orientation) {
      Orientation.Export -> rustDeclare()
      Orientation.Import -> rustDeclareTrampoline()
    }
  }

  fun BufferedSink.rustDeclareTrampoline() {
    val modifiers = when (callingMechanism) {
      is CallingMechanism.Async -> "async "
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |    ${modifiers}fn ${identifier.lowerSnakeCase}_trampoline(index: i32) -> i32 {
      |
      """.trimMargin(),
    )

    when (passingMechanism) {
      is PassingMechanism.Parameter -> {
        val value = indexToValueFunction.rustCall("Self", "index", "panic!()")
        val call = rustCall("bindings", "panic!()", "value")
        writeUtf8(
          """
          |        let value = ${value.replace("\n", "\n        ")};
          |        ${call.replace("\n", "\n        ")}
          |    }
          |
          """.trimMargin(),
        )
      }

      PassingMechanism.Return -> {
        val call = rustCall("bindings", "index", "panic!()")
        val resultIndex = valueToIndexFunction.rustCall("Self", "panic!()", "value")
        writeUtf8(
          """
          |        let value = ${call.replace("\n", "\n        ")};
          |        ${resultIndex.replace("\n", "\n        ")}
          |    }
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
      |    ${modifiers}fn ${identifier.lowerSnakeCase}(
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
      is PassingMechanism.Parameter -> {
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

      PassingMechanism.Return -> {
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

  fun BufferedSink.kotlinDeclareGuestFunctions() {
    when (orientation) {
      Orientation.Export -> kotlinDeclare()
      Orientation.Import -> kotlinDeclareTrampoline()
    }
  }

  fun BufferedSink.kotlinDeclareHostFunctions() {
    when (orientation) {
      Orientation.Export -> {}
      Orientation.Import -> kotlinDeclare()
    }
  }

  fun BufferedSink.kotlinDeclareTrampoline() {
    val modifiers = when (callingMechanism) {
      is CallingMechanism.Async -> " suspend"
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |  override$modifiers fun ${identifier.lowerCamelCase}Trampoline(index: Int): Int {
      |
      """.trimMargin(),
    )

    when (passingMechanism) {
      is PassingMechanism.Parameter -> {
        val value = indexToValueFunction.kotlinCall("this", "index", "Nothing")
        val call = kotlinCall("BrevityTest.host", "Nothing", value)
        writeUtf8(
          """
          |    return ${call.replace("\n", "\n    ")}
          |  }
          |
          """.trimMargin(),
        )
      }

      PassingMechanism.Return -> {
        val call = kotlinCall("BrevityTest.host", "index", "Nothing")
        val resultIndex = valueToIndexFunction.kotlinCall("this", "Nothing", "value")
        writeUtf8(
          """
          |    val value = ${call.replace("\n", "\n    ")}
          |    return ${resultIndex.replace("\n", "\n    ")}
          |  }
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
      |  override$modifiers fun ${identifier.lowerCamelCase}(
      |
      """.trimMargin(),
    )

    val padding = (passingMechanism as? PassingMechanism.Parameter)?.padding ?: 0
    for (i in 0 until padding) {
      writeUtf8(
        """
        |    p$i: Int,
        |
        """.trimMargin(),
      )
    }

    when (passingMechanism) {
      is PassingMechanism.Parameter -> {
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

      PassingMechanism.Return -> {
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

  fun kotlinCall(
    callTarget: String,
    valueIndex: String,
    value: String,
  ): String {
    val call = Buffer().apply {
      writeUtf8(
        """
        |$callTarget.${identifier.lowerCamelCase}(
        |
        """.trimMargin(),
      )

      for (i in 0 until padding) {
        writeUtf8(
          """
          |  p$i = 0,
          |
          """.trimMargin(),
        )
      }

      when (passingMechanism) {
        is PassingMechanism.Parameter -> {
          writeUtf8(
            """
            |  v = ${value.replace("\n", "\n  ")},
            |
            """.trimMargin(),
          )
        }

        PassingMechanism.Return -> {
          writeUtf8(
            """
            |  index = ${valueIndex},
            |
            """.trimMargin(),
          )
        }
      }
      writeUtf8(
        """
        |)
        """.trimMargin(),
      )
    }
    return call.readUtf8()
  }

  fun rustCall(
    callTarget: String,
    valueIndex: String,
    value: String,
  ): String {
    val call = Buffer().apply {
      writeUtf8(
        """
        |$callTarget::${identifier.lowerSnakeCase}(
        |
        """.trimMargin(),
      )

      for (i in 0 until padding) {
        writeUtf8(
          """
          |    0,
          |
          """.trimMargin(),
        )
      }

      when (passingMechanism) {
        is PassingMechanism.Parameter -> {
          writeUtf8(
            """
            |    ${value.replace("\n", "\n    ")},
            |
            """.trimMargin(),
          )
        }

        PassingMechanism.Return -> {
          writeUtf8(
            """
            |    ${valueIndex},
            |
            """.trimMargin(),
          )
        }
      }
      when (callingMechanism) {
        is CallingMechanism.Async -> {
          writeUtf8(
            """
            |).await
            """.trimMargin(),
          )
        }

        CallingMechanism.Sync -> {
          writeUtf8(
            """
            |)
            """.trimMargin(),
          )
        }
      }
    }
    return call.readUtf8()
  }

  fun kotlinCallTrampoline(
    callTarget: String,
    valueIndex: Int,
  ) = "$callTarget.${identifier.lowerCamelCase}Trampoline($valueIndex)"

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

  private val Orientation.keyword: String
    get() = when (this) {
      Orientation.Export -> "export"
      Orientation.Import -> "import"
    }
}

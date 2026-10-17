package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import dev.wasmo.brevity.kotlin.generator.lowerSnakeCase
import okio.Buffer
import okio.BufferedSink

data class TestFunction(
  val functionParent: FunctionParent,
  val passingMechanism: PassingMechanism,
  val callingMechanism: CallingMechanism,
  val type: SampleType,
) {
  val orientation: Orientation
    get() = functionParent.orientation

  val testCases: List<TestCase>
    get() = type.values.indices.map { TestCase(this, it) }

  val futures: Boolean
    get() = false
  val sleep: Boolean
    get() = false

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
      if (callingMechanism == CallingMechanism.Async) {
        add("async")
      }
    }.joinToString(separator = "-"),
  )

  val trampolineIdentifier: Identifier
    get() = Identifier("$identifier-${functionParent.keyword}-trampoline")

  val padding: Int
    get() = (passingMechanism as? PassingMechanism.Parameter)?.padding ?: 0

  private val indexToValueFunction: TestFunction
    get() = copy(
      passingMechanism = PassingMechanism.Return,
      callingMechanism = CallingMechanism.Sync,
      functionParent = functionParent.export(),
    )

  private val valueToIndexFunction: TestFunction
    get() = copy(
      passingMechanism = PassingMechanism.Parameter(),
      callingMechanism = CallingMechanism.Sync,
      functionParent = functionParent.export(),
    )

  override fun toString() = identifier.name

  fun BufferedSink.witDeclare() {
    val orientationModifier = when {
      functionParent.isWorld -> "${orientation.keyword} "
      else -> ""
    }

    val syncModifier = when (callingMechanism) {
      CallingMechanism.Async -> " async"
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |$orientationModifier$identifier:$syncModifier func(
      |
      """.trimMargin(),
    )
    for (i in 0 until padding) {
      writeUtf8(
        """
        |  p$i: s32,
        |
        """.trimMargin(),
      )
    }
    when (passingMechanism) {
      is PassingMechanism.Parameter -> {
        writeUtf8(
          """
          |  v: ${type.witType},
          |) -> s32;
          |
          """.trimMargin(),
        )
      }

      PassingMechanism.Return -> {
        writeUtf8(
          """
          |  index: s32,
          |) -> ${type.witType};
          |
          """.trimMargin(),
        )
      }
    }
  }

  fun BufferedSink.witDeclareTrampoline() {
    val syncModifier = when (callingMechanism) {
      CallingMechanism.Async -> " async"
      CallingMechanism.Sync -> ""
    }
    writeUtf8(
      """
      |export $trampolineIdentifier:$syncModifier func(index: s32) -> s32;
      |
      """.trimMargin(),
    )
  }

  fun BufferedSink.rustDeclareTrampoline() {
    val modifiers = when (callingMechanism) {
      CallingMechanism.Async -> "async "
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |    ${modifiers}fn ${trampolineIdentifier.lowerSnakeCase}(index: i32) -> i32 {
      |
      """.trimMargin(),
    )

    val callTarget = when {
      functionParent.isWorld -> "bindings::"
      functionParent.isResource -> {
        writeUtf8(
          """
          |        let resource = bindings::brevity::testing::${functionParent.serviceName.lowerSnakeCase}::create();
          |
          """.trimMargin(),
        )
        "resource."
      }

      else -> "bindings::brevity::testing::${functionParent.identifier.lowerSnakeCase}::"
    }

    when (passingMechanism) {
      is PassingMechanism.Parameter -> {
        val value = indexToValueFunction.rustCall("Self::", "index", "panic!()")
        val call = rustCall(callTarget, "panic!()", "value")
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
        val call = rustCall(callTarget, "index", "panic!()")
        val resultIndex = valueToIndexFunction.rustCall("Self::", "panic!()", "value")
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
    rustDeclareWithSignature {
      maybeWriteSleepRust()

      when (passingMechanism) {
        is PassingMechanism.Parameter -> {
          if (type.compareAsString) {
            writeUtf8(
              """
              |let v_str = v.to_string();
              |
              """.trimMargin(),
            )
            for ((index, value) in type.values.withIndex()) {
              writeUtf8(
                """
                |if v_str == (${value.rust}).to_string() { return $index }
                |
                """.trimMargin(),
              )
            }
          } else {
            for ((index, value) in type.values.withIndex()) {
              writeUtf8(
                """
                |if v == ${value.rust} { return $index }
                |
                """.trimMargin(),
              )
            }
          }
          writeUtf8(
            """
            |panic!("unexpected value")
            |
            """.trimMargin(),
          )
        }

        PassingMechanism.Return -> {
          writeUtf8(
            """
            |match index {
            |
            """.trimMargin(),
          )
          for ((index, value) in type.values.withIndex()) {
            writeUtf8(
              """
              |    $index => (${value.rust}).to_owned(),
              |
              """.trimMargin(),
            )
          }
          writeUtf8(
            """
            |    _ => panic!("unexpected index {}", index)
            |}
            |
            """.trimMargin(),
          )
        }
      }
    }
  }

  private fun BufferedSink.rustDeclareFunctionStub() {
    rustDeclareWithSignature {
      writeUtf8(
        """
        |panic!("unexpected call")
        |
        """.trimMargin(),
      )
    }
  }

  private fun BufferedSink.rustDeclareWithSignature(body: BufferedSink.() -> Unit) {
    val bodyString = Buffer().run {
      body()
      readUtf8().trim()
    }

    val modifiers = when (callingMechanism) {
      CallingMechanism.Async -> "async "
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |    ${modifiers}fn ${identifier.lowerSnakeCase}(
      |
      """.trimMargin(),
    )
    if (functionParent.isResource) {
      writeUtf8(
        """
        |        &self,
        |
        """.trimMargin(),
      )
    }

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
      }

      PassingMechanism.Return -> {
        writeUtf8(
          """
          |        index: i32
          |    ) -> ${type.rustType} {
          |
          """.trimMargin(),
        )
      }
    }

    writeUtf8(
      """
      |        ${bodyString.replace("\n", "\n        ")}
      |    }
      |
      """.trimMargin(),
    )
  }

  fun BufferedSink.kotlinDeclareTrampoline() {
    val modifiers = when (callingMechanism) {
      CallingMechanism.Async -> " suspend"
      CallingMechanism.Sync -> ""
    }

    writeUtf8(
      """
      |override$modifiers fun ${trampolineIdentifier.lowerCamelCase}(index: Int): Int {
      |
      """.trimMargin(),
    )

    val callTarget = when {
      functionParent.isWorld -> "TestWorld.host"
      functionParent.isResource ->
        "TestWorld.host.${functionParent.serviceName.lowerCamelCase}.create()"

      else -> "TestWorld.host.${functionParent.serviceName.lowerCamelCase}"
    }

    when (passingMechanism) {
      is PassingMechanism.Parameter -> {
        val value = indexToValueFunction.kotlinCall("this", "index", "Nothing")
        val call = kotlinCall(callTarget, "Nothing", value)
        writeUtf8(
          """
          |  return ${call.replace("\n", "\n    ")}
          |}
          |
          """.trimMargin(),
        )
      }

      PassingMechanism.Return -> {
        val call = kotlinCall(callTarget, "index", "Nothing")
        val resultIndex = valueToIndexFunction.kotlinCall("this", "Nothing", "value")
        writeUtf8(
          """
          |  val value = ${call.replace("\n", "\n    ")}
          |  return ${resultIndex.replace("\n", "\n    ")}
          |}
          |
          """.trimMargin(),
        )
      }
    }
  }

  fun BufferedSink.kotlinDeclare() {
    kotlinDeclareWithSignature {
      maybeWriteSleepKotlin()

      when (passingMechanism) {
        is PassingMechanism.Parameter -> {
          if (type.compareAsString) {
            writeUtf8(
              """
              |val v_str = v.toString()
              |
              """.trimMargin(),
            )
            for ((index, value) in type.values.withIndex()) {
              writeUtf8(
                """
                |if (v_str == (${value.kotlin}).toString()) { return $index }
                |
                """.trimMargin(),
              )
            }
          } else {
            for ((index, value) in type.values.withIndex()) {
              if (type.kotlinEqualityMethod == null) {
                writeUtf8(
                  """
                  |if (v == ${value.kotlin}) { return $index }
                  |
                  """.trimMargin(),
                )
              } else {
                writeUtf8(
                  """
                  |if (v.${type.kotlinEqualityMethod}(${value.kotlin})) { return $index }
                  |
                  """.trimMargin(),
                )
              }
            }
          }
          writeUtf8(
            """
            |return -1
            """.trimMargin(),
          )
        }

        PassingMechanism.Return -> {
          writeUtf8(
            """
            |return when (index) {
            |
            """.trimMargin(),
          )
          for ((index, value) in type.values.withIndex()) {
            writeUtf8(
              """
              |  $index -> ${value.kotlin}
              |
              """.trimMargin(),
            )
          }
          writeUtf8(
            $$"""
            |  else -> error("unexpected index: $index")
            |}
            |
            """.trimMargin(),
          )
        }
      }
    }
  }

  private fun BufferedSink.kotlinDeclareWithSignature(body: BufferedSink.() -> Unit) {
    val bodyString = Buffer().run {
      body()
      readUtf8().trim()
    }

    val modifiers = when (callingMechanism) {
      CallingMechanism.Async -> " suspend"
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
      }

      PassingMechanism.Return -> {
        writeUtf8(
          """
          |    index: Int,
          |  ): ${type.kotlinType} {
          |
          """.trimMargin(),
        )
      }
    }

    writeUtf8(
      """
      |    ${bodyString.replace("\n", "\n    ")}
      |  }
      |
      """.trimMargin(),
    )
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
        |$callTarget${identifier.lowerSnakeCase}(
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

      val transformedValue = when {
        orientation == Import && callingMechanism == CallingMechanism.Sync ->
          type.borrowRustValue(value)

        else -> value
      }

      when (passingMechanism) {
        is PassingMechanism.Parameter -> {
          writeUtf8(
            """
            |    ${transformedValue.replace("\n", "\n    ")},
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
        CallingMechanism.Async -> {
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
  ) = "$callTarget.${trampolineIdentifier.lowerCamelCase}($valueIndex)"

  private fun BufferedSink.maybeWriteSleepKotlin() {
    if (!sleep) return
    writeUtf8(
      """
      |    delay(100.milliseconds)
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.maybeWriteSleepRust() {
    if (!sleep) return
    writeUtf8(
      """
      |        sleep(Duration::from_millis(4000));
      |
      """.trimMargin(),
    )
  }

  private val Orientation.keyword: String
    get() = when (this) {
      Export -> "export"
      Import -> "import"
    }
}

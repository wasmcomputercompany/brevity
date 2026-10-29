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

  // TODO: add a TestCase(this, 0, 100UL) to trigger a sleep once Rust is on WASIp3.
  val testCases: List<TestCase>
    get() = type.values.indices.map { TestCase(this, it, 0UL) }

  val futures: Boolean
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

    when (callingMechanism) {
      CallingMechanism.Async -> {
        writeUtf8(
          """
          |$orientationModifier$identifier: async func(
          |  delay-millis: u64,
          |
          """.trimMargin(),
        )
      }

      CallingMechanism.Sync -> {
        writeUtf8(
          """
          |$orientationModifier$identifier: func(
          |
          """.trimMargin(),
        )
      }
    }

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
    when (callingMechanism) {
      CallingMechanism.Async -> {
        writeUtf8(
          """
          |export $trampolineIdentifier: async func(delay-millis: u64, index: s32) -> s32;
          |
          """.trimMargin(),
        )
      }

      CallingMechanism.Sync -> {
        writeUtf8(
          """
          |export $trampolineIdentifier: func(index: s32) -> s32;
          |
          """.trimMargin(),
        )
      }
    }
  }

  fun BufferedSink.rustDeclareTrampoline() {
    when (callingMechanism) {
      CallingMechanism.Async -> {
        writeUtf8(
          """
          |    async fn ${trampolineIdentifier.lowerSnakeCase}(delay_millis: u64, index: i32) -> i32 {
          |
          """.trimMargin(),
        )
      }

      CallingMechanism.Sync -> {
        writeUtf8(
          """
          |    fn ${trampolineIdentifier.lowerSnakeCase}(index: i32) -> i32 {
          |
          """.trimMargin(),
        )
      }
    }

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
      if (callingMechanism == CallingMechanism.Async) {
        writeUtf8(
          """
          |if delay_millis > 0 { sleep(Duration::from_millis(delay_millis)); }
          |
          """.trimMargin(),
        )
      }

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

    when (callingMechanism) {
      CallingMechanism.Async -> {
        writeUtf8(
          """
          |    async fn ${identifier.lowerSnakeCase}(
          |
          """.trimMargin(),
        )
      }

      CallingMechanism.Sync -> {
        writeUtf8(
          """
          |    fn ${identifier.lowerSnakeCase}(
          |
          """.trimMargin(),
        )
      }
    }

    if (functionParent.isResource) {
      writeUtf8(
        """
        |        &self,
        |
        """.trimMargin(),
      )
    }

    if (callingMechanism == CallingMechanism.Async) {
      writeUtf8(
        """
        |        delay_millis: u64,
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
    when (callingMechanism) {
      CallingMechanism.Async -> {
        writeUtf8(
          """
          |override suspend fun ${trampolineIdentifier.lowerCamelCase}(delayMillis: ULong, index: Int): Int {
          |
          """.trimMargin(),
        )
      }

      CallingMechanism.Sync -> {
        writeUtf8(
          """
          |override fun ${trampolineIdentifier.lowerCamelCase}(index: Int): Int {
          |
          """.trimMargin(),
        )
      }
    }

    val callTarget = when {
      functionParent.isWorld -> "TestWorld.host"
      functionParent.isResource ->
        "TestWorld.host.${functionParent.serviceName.lowerCamelCase}.create()"

      else -> "TestWorld.host.${functionParent.serviceName.lowerCamelCase}"
    }

    when (passingMechanism) {
      is PassingMechanism.Parameter -> {
        val value = indexToValueFunction.kotlinCall("this", "index", "Nothing")
        val call = kotlinCall(callTarget, "Nothing", value, "delayMillis")
        writeUtf8(
          """
          |  return ${call.replace("\n", "\n  ")}
          |}
          |
          """.trimMargin(),
        )
      }

      PassingMechanism.Return -> {
        val call = kotlinCall(callTarget, "index", "Nothing", "delayMillis")
        val resultIndex = valueToIndexFunction.kotlinCall("this", "Nothing", "value")
        writeUtf8(
          """
          |  val value = ${call.replace("\n", "\n  ")}
          |  return ${resultIndex.replace("\n", "\n  ")}
          |}
          |
          """.trimMargin(),
        )
      }
    }
  }

  fun BufferedSink.kotlinDeclare() {
    kotlinDeclareWithSignature {
      if (callingMechanism == CallingMechanism.Async) {
        writeUtf8(
          """
          |if (delayMillis > 0UL) delay(delayMillis.toLong().milliseconds)
          |
          """.trimMargin(),
        )
      }

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

    when (callingMechanism) {
      CallingMechanism.Async -> {
        writeUtf8(
          """
          |  override suspend fun ${identifier.lowerCamelCase}(
          |    delayMillis: ULong,
          |
          """.trimMargin(),
        )
      }

      CallingMechanism.Sync -> {
        writeUtf8(
          """
          |  override fun ${identifier.lowerCamelCase}(
          |
          """.trimMargin(),
        )
      }
    }

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
    delayMillis: String? = null,
  ): String {
    val call = Buffer().apply {
      writeUtf8(
        """
        |$callTarget.${identifier.lowerCamelCase}(
        |
        """.trimMargin(),
      )

      if (delayMillis != null && callingMechanism == CallingMechanism.Async) {
        writeUtf8(
          """
          |  delayMillis = $delayMillis,
          |
          """.trimMargin(),
        )
      }

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

      if (callingMechanism == CallingMechanism.Async) {
        writeUtf8(
          """
          |    delay_millis,
          |
          """.trimMargin(),
        )
      }

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
    delayMillis: ULong,
  ): String = buildString {
    append("$callTarget.${trampolineIdentifier.lowerCamelCase}(")
    if (callingMechanism == CallingMechanism.Async) {
      append("${delayMillis}UL, ")
    }
    append("$valueIndex)")
  }

  private val Orientation.keyword: String
    get() = when (this) {
      Export -> "export"
      Import -> "import"
    }
}

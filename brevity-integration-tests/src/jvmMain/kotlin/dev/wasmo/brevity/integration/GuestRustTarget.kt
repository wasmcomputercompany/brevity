package dev.wasmo.brevity.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class GuestRustTarget(
  private val name: String,
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val types: List<SampleType>,
) {
  suspend fun generate() {
    withContext(Dispatchers.IO + CoroutineName("GuestRustTarget")) {
      val path = layout.rustSrc / "${name}_lib.rs"
      fileSystem.createDirectories(layout.rustSrc)
      fileSystem.write(path) { writeRust() }
    }
  }

  private fun BufferedSink.writeRust() {
    if (types.any { it.futures }) {
      writeUtf8(
        """
        |use crate::bindings::wit_future::FuturePayload;
        |use wit_bindgen::FutureReader;
        |extern crate futures;
        |
        |
        """.trimMargin(),
      )
    }
    writeUtf8(
      """
      |mod bindings {
      |    wit_bindgen::generate!({
      |        path: "../wit/bridge-type-$name-test.wit",
      |    });
      |
      |    use super::BrevityTesting;
      |    export!(BrevityTesting);
      |}
      |
      |struct BrevityTesting;
      |
      |impl bindings::Guest for BrevityTesting {
      |
      """.trimMargin(),
    )
    for (type in types) {
      passAsParameter(
        type = type,
      )
      passAsParameter(
        type = type,
        padding = 16,
      )
      passAsReturnValue(type)
      if (type.async) {
        asyncReturnValue(type)
      }
      if (type.futures) {
        asyncFutureReturnValue(type)
        futureReturnValue(type)
      }
    }
    writeUtf8(
      """
      |}
      """.trimMargin(),
    )
  }

  private fun BufferedSink.passAsParameter(
    type: SampleType,
    padding: Int = 0,
  ) {
    val paddingSuffix = when {
      padding > 0 -> "_p$padding"
      else -> ""
    }

    writeUtf8(
      """
      |    fn pass_as_parameter_${type.idLowerSnake}$paddingSuffix(
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
    writeUtf8(
      """
      |        v: ${type.rustType}
      |    ) -> i32 {
      |
      """.trimMargin(),
    )

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

  private fun BufferedSink.passAsReturnValue(type: SampleType) {
    writeUtf8(
      """
      |    fn pass_as_return_value_${type.idLowerSnake}(index: i32) -> ${type.rustType} {
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

  private fun BufferedSink.asyncReturnValue(type: SampleType) {
    writeUtf8(
      """
      |    async fn async_return_value_${type.idLowerSnake}(
      |        index: i32
      |    ) -> ${type.rustType} {
      |        Self::pass_as_return_value_${type.idLowerSnake}(index)
      |    }
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.asyncFutureReturnValue(type: SampleType) {
    writeUtf8(
      """
      |    async fn async_future_return_value_${type.idLowerSnake}(
      |        index: i32
      |    ) -> FutureReader<${type.rustType}> {
      |        Self::future_return_value_${type.idLowerSnake}(index)
      |    }
      |
      """.trimMargin(),
    )
  }

  private fun BufferedSink.futureReturnValue(type: SampleType) {
    writeUtf8(
      """
      |    fn future_return_value_${type.idLowerSnake}(
      |        index: i32
      |    ) -> FutureReader<${type.rustType}> {
      |        let (future_writer, future_reader) = unsafe {
      |            wit_bindgen::rt::async_support::future_new(
      |                || -> ${type.rustType} { panic!("future default") },
      |                ${type.rustType}::VTABLE,
      |            )
      |        };
      |        future_writer.write(Self::pass_as_return_value_${type.idLowerSnake}(index));
      |        future_reader
      |    }
      |
      """.trimMargin(),
    )
  }
}

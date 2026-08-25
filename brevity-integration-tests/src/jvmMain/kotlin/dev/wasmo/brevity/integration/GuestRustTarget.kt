package dev.wasmo.brevity.integration

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class GuestRustTarget(
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val types: List<SampleType>,
) {
  suspend fun generate() {
    withContext(Dispatchers.IO + CoroutineName("GuestRustTarget")) {
      val path = layout.rustSrc / "lib.rs"
      fileSystem.createDirectories(layout.rustSrc)
      fileSystem.write(path) { writeRust() }
    }
  }

  private fun BufferedSink.writeRust() {
    writeUtf8(
      """
      |mod bindings {
      |    wit_bindgen::generate!({
      |        path: "../wit/bridge-type-test.wit",
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
      if (!type.mustAllocate) {
        writeUtf8(
          """
          |  fn pass_as_parameter_${type.idLowerSnake}(v: ${type.rustType}) -> i32 {
          |
          """.trimMargin(),
        )

        if (type.compareAsString) {
          writeUtf8(
            """
            |    let v_str = v.to_string();
            |
            """.trimMargin(),
          )
          for ((index, value) in type.values.withIndex()) {
            writeUtf8(
              """
              |    if v_str == (${value.rust}).to_string() { return $index }
              |
              """.trimMargin(),
            )
          }
          writeUtf8(
            """
            |    panic!("unexpected value")
            |  }
            |
            """.trimMargin(),
          )
        } else {
          writeUtf8(
            """
            |    match v {
            |
            """.trimMargin(),
          )
          for ((index, value) in type.values.withIndex()) {
            writeUtf8(
              """
              |      ${value.rust} => $index,
              |
              """.trimMargin(),
            )
          }
          writeUtf8(
            """
            |      _ => panic!("unexpected value")
            |    }
            |  }
            |
            """.trimMargin(),
          )
        }
      }

      writeUtf8(
        """
        |  fn pass_as_return_value_${type.idLowerSnake}(index: i32) -> ${type.rustType} {
        |    match index {
        |
        """.trimMargin(),
      )
      for ((index, value) in type.values.withIndex()) {
        writeUtf8(
          """
          |      $index => (${value.rust}).to_owned(),
          |
          """.trimMargin(),
        )
      }
      writeUtf8(
        """
        |      _ => panic!("unexpected index {}", index)
        |    }
        |  }
        |
        """.trimMargin(),
      )
    }
    writeUtf8(
      """
      |}
      """.trimMargin(),
    )
  }
}

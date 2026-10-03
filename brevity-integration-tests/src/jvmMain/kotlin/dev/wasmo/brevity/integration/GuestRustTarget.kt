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
        """.trimMargin(),
      )
    }
    writeUtf8(
      """
      |use std::thread::sleep;
      |use std::time::Duration;
      |
      |
      """.trimMargin(),
    )
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

    for (testFunction in types.testFunctions) {
      with(testFunction) {
        rustDeclareGuestFunctions()
      }
    }
    writeUtf8(
      """
      |}
      """.trimMargin(),
    )
  }
}

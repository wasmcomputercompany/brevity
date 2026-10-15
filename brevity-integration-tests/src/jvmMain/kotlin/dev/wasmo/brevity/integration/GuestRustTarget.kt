package dev.wasmo.brevity.integration

import dev.wasmo.brevity.kotlin.generator.lowerSnakeCase
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSink
import okio.FileSystem

class GuestRustTarget(
  private val name: String,
  private val fileSystem: FileSystem,
  private val layout: ProjectLayout,
  private val testFunctions: List<TestFunction>,
) {
  suspend fun generate() {
    withContext(Dispatchers.IO + CoroutineName("GuestRustTarget")) {
      val path = layout.rustSrc / "${name}_lib.rs"
      fileSystem.createDirectories(layout.rustSrc)
      fileSystem.write(path) { writeRust() }
    }
  }

  private fun BufferedSink.writeRust() {
    writeUtf8(
      """
      |#![allow(unused_imports)]
      |
      """.trimMargin(),
    )
    if (testFunctions.any { it.futures }) {
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
      |
      """.trimMargin(),
    )

    val parentToTestFunctions = testFunctions.groupBy { it.functionParent }
    for ((parent, childFunctions) in parentToTestFunctions) {
      when (parent) {
        FunctionParent.World -> {
          writeUtf8(
            """
            |impl bindings::Guest for BrevityTesting {
            |
            """.trimMargin(),
          )
        }

        FunctionParent.Interface, FunctionParent.Resource -> {
          writeUtf8(
            """
            |impl bindings::exports::brevity::testing::${parent.identifier.lowerSnakeCase}::Guest for BrevityTesting {
            |
            """.trimMargin(),
          )
        }
      }

      for (testFunction in childFunctions) {
        with(testFunction) {
          rustDeclareGuestFunctions()
        }
      }

      writeUtf8(
        """
        |}
        |
        |
        """.trimMargin(),
      )
    }
  }
}

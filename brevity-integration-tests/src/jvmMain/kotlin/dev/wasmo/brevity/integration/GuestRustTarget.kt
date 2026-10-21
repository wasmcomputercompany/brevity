package dev.wasmo.brevity.integration

import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.kotlin.generator.lowerSnakeCase
import dev.wasmo.brevity.kotlin.generator.upperCamelCase
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
      |        additional_derives: [PartialEq],
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
      val serviceName = parent.serviceName.lowerSnakeCase
      when (parent) {
        FunctionParent.WorldExport -> {
          writeUtf8(
            """
            |impl bindings::Guest for BrevityTesting {
            |
            """.trimMargin(),
          )
          for (memberServiceFunctions in parentToTestFunctions.values) {
            for (testFunction in memberServiceFunctions.filter { it.orientation == Import }) {
              with(testFunction) {
                rustDeclareTrampoline()
              }
            }
          }
        }

        FunctionParent.InterfaceExport -> {
          writeUtf8(
            """
            |impl bindings::exports::brevity::testing::$serviceName::Guest for BrevityTesting {
            |
            """.trimMargin(),
          )
        }

        FunctionParent.ResourceExport -> {
          val resourceName = parent.identifier.upperCamelCase
          writeUtf8(
            """
            |struct Real$resourceName {
            |}
            |
            |impl bindings::exports::brevity::testing::$serviceName::Guest for BrevityTesting {
            |    type $resourceName = Real$resourceName;
            |
            |    fn create() -> bindings::exports::brevity::testing::$serviceName::$resourceName {
            |        bindings::exports::brevity::testing::$serviceName::$resourceName::new(Real$resourceName {})
            |    }
            |}
            |
            |impl bindings::exports::brevity::testing::$serviceName::Guest$resourceName for Real$resourceName {
            |
            """.trimMargin(),
          )
        }

        else -> continue
      }

      for (testFunction in childFunctions.filter { it.orientation == Export }) {
        with(testFunction) {
          rustDeclare()
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

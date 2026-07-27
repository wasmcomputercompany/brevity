package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.FunctionNameConstructor
import dev.wasmo.brevity.FunctionNameInterface
import dev.wasmo.brevity.FunctionNameMethod
import dev.wasmo.brevity.FunctionNameStatic
import dev.wasmo.brevity.FunctionNameWorld
import kotlin.test.Test

class NamesTest {
  @Test
  fun `function on world`() {
    val function = FunctionNameWorld(
      name = "sum",
    )
    assertThat(function.importFunctionName).isEqualTo("sum_import")
    assertThat(function.exportFunctionName).isEqualTo("sum_export")
  }

  @Test
  fun `function on interface`() {
    val function = FunctionNameInterface(
      serviceName = "wasi:http/types@0.3.0",
      name = "has",
    )
    assertThat(function.importFunctionName).isEqualTo("types_has_import")
    assertThat(function.exportFunctionName).isEqualTo("types_has_export")
  }

  @Test
  fun `package name with sem ver`() {
    val function = FunctionNameConstructor(
      serviceName = "wasi:http/types@0.3.0",
      name = "fields",
    )
    assertThat(function.importFunctionName).isEqualTo("types_fields_import")
    assertThat(function.exportFunctionName).isEqualTo("types_fields_export")
  }

  @Test
  fun `function on resource`() {
    val function = FunctionNameMethod(
      serviceName = "wasi:http/types@0.3.0",
      name = "from-list",
      resourceName = "fields",
    )
    assertThat(function.importFunctionName).isEqualTo("types_fields_fromList_import")
    assertThat(function.exportFunctionName).isEqualTo("types_fields_fromList_export")
  }

  @Test
  fun `static function`() {
    val function = FunctionNameStatic(
      serviceName = "wasi:http/types@0.3.0",
      name = "has",
      resourceName = "fields",
    )
    assertThat(function.importFunctionName).isEqualTo("types_fields_has_import")
    assertThat(function.exportFunctionName).isEqualTo("types_fields_has_export")
  }
}

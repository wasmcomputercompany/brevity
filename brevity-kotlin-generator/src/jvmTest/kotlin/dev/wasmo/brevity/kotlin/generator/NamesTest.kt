package dev.wasmo.brevity.kotlin.generator

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.wasmo.brevity.Annotation
import dev.wasmo.brevity.FunctionName
import kotlin.test.Test

class NamesTest {
  @Test
  fun `function on world`() {
    val function = FunctionName(
      name = "sum",
    )
    assertThat(function.importFunctionName).isEqualTo("sum_import")
    assertThat(function.exportFunctionName).isEqualTo("sum_export")
  }

  @Test
  fun `function on interface`() {
    val function = FunctionName(
      serviceName = "wasi:http/types@0.3.0",
      name = "has",
    )
    assertThat(function.importFunctionName).isEqualTo("types_has_import")
    assertThat(function.exportFunctionName).isEqualTo("types_has_export")
  }

  @Test
  fun `package name with sem ver`() {
    val function = FunctionName(
      serviceName = "wasi:http/types@0.3.0",
      name = "fields",
      annotation = Annotation.Constructor,
    )
    assertThat(function.importFunctionName).isEqualTo("types_fields_import")
    assertThat(function.exportFunctionName).isEqualTo("types_fields_export")
  }

  @Test
  fun `function on resource`() {
    val function = FunctionName(
      serviceName = "wasi:http/types@0.3.0",
      name = "from-list",
      resourceName = "fields",
      annotation = Annotation.Static,
    )
    assertThat(function.importFunctionName).isEqualTo("types_fields_fromList_import")
    assertThat(function.exportFunctionName).isEqualTo("types_fields_fromList_export")
  }

  @Test
  fun `static function`() {
    val function = FunctionName(
      serviceName = "wasi:http/types@0.3.0",
      name = "has",
      resourceName = "fields",
      annotation = Annotation.Method,
    )
    assertThat(function.importFunctionName).isEqualTo("types_fields_has_import")
    assertThat(function.exportFunctionName).isEqualTo("types_fields_has_export")
  }
}

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
    assertThat(function.fullyQualifiedKotlinName).isEqualTo("sum")
  }

  @Test
  fun `function on interface`() {
    val function = FunctionName(
      packageName = "wasi:http@0.3.0",
      parentName = "types",
      name = "has",
    )
    assertThat(function.fullyQualifiedKotlinName).isEqualTo("wasi_http_v0_3_0_types_has")
  }

  @Test
  fun `package name with sem ver`() {
    val function = FunctionName(
      packageName = "wasi:http@0.3.0",
      parentName = "types",
      name = "fields",
      annotation = Annotation.Constructor,
    )
    assertThat(function.fullyQualifiedKotlinName).isEqualTo("wasi_http_v0_3_0_types_fields")
  }

  @Test
  fun `function on resource`() {
    val function = FunctionName(
      packageName = "wasi:http@0.3.0",
      parentName = "types",
      name = "from-list",
      resourceName = "fields",
      annotation = Annotation.Static,
    )
    assertThat(function.fullyQualifiedKotlinName)
      .isEqualTo("wasi_http_v0_3_0_types_fields_fromList")
  }

  @Test
  fun `static function`() {
    val function = FunctionName(
      packageName = "wasi:http@0.3.0",
      parentName = "types",
      name = "has",
      resourceName = "fields",
      annotation = Annotation.Method,
    )
    assertThat(function.fullyQualifiedKotlinName).isEqualTo("wasi_http_v0_3_0_types_fields_has")
  }
}

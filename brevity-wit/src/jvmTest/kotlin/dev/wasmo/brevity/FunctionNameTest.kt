package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class FunctionNameTest {
  @Test
  fun `function on world`() {
    val function = FunctionName(
      name = "sum",
    )
    assertThat(function.moduleName).isNull()
    assertThat(function.abiName).isEqualTo("sum")
  }

  @Test
  fun `function on interface`() {
    val function = FunctionName(
      serviceName = "wasi:http/types@0.3.0",
      name = "has",
    )
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("has")
  }

  @Test
  fun `package name with sem ver`() {
    val function = FunctionName(
      serviceName = "wasi:http/types@0.3.0",
      name = "fields",
      annotation = Annotation.Constructor,
    )
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[constructor]fields")
  }

  @Test
  fun `function on resource`() {
    val function = FunctionName(
      serviceName = "wasi:http/types@0.3.0",
      name = "from-list",
      resourceName = "fields",
      annotation = Annotation.Static,
    )
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[static]fields.from-list")
  }

  @Test
  fun `static function`() {
    val function = FunctionName(
      serviceName = "wasi:http/types@0.3.0",
      name = "has",
      resourceName = "fields",
      annotation = Annotation.Method,
    )
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[method]fields.has")
  }
}

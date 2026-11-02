package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.wasmo.brevity.io.toServiceName
import kotlin.test.Test

class FunctionNameTest {
  private val resourceParent = TypeName.Declared(
    serviceName = "wasi:http/types@0.3.0".toServiceName(),
    name = Identifier("fields"),
  )

  @Test
  fun `export function on world`() {
    val serviceName = "wasi:http/proxy@0.3.0".toServiceName()
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.WorldExport(serviceName),
      name = Identifier("sum"),
    )
    assertThat(function.moduleName).isNull()
    assertThat(function.abiName).isEqualTo("sum")
  }

  @Test
  fun `import function on world`() {
    val serviceName = "wasi:http/proxy@0.3.0".toServiceName()
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.WorldImport(serviceName),
      name = Identifier("sum"),
    )
    assertThat(function.moduleName).isEqualTo($$"$root")
    assertThat(function.abiName).isEqualTo("sum")
  }

  @Test
  fun `function on interface`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Interface("wasi:http/types@0.3.0".toServiceName()),
      name = Identifier("has"),
    )
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("has")
  }

  @Test
  fun `function on resource`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Resource(resourceParent),
      name = Identifier("from-list"),
    )
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[method]fields.from-list")
  }

  @Test
  fun `async lift resource function`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Resource(resourceParent),
      name = Identifier("from-list"),
      transform = FunctionName.Transform.AsyncExport,
    )
    assertThat(function.moduleName).isEqualTo("[async-lift]wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[method]fields.from-list")
  }

  @Test
  fun `async lift world function`() {
    val serviceName = "wasi:http/proxy@0.3.0".toServiceName()
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.WorldExport(serviceName),
      name = Identifier("from-list"),
      transform = FunctionName.Transform.AsyncExport,
    )
    assertThat(function.moduleName).isNull()
    assertThat(function.abiName).isEqualTo("[async-lift]from-list")
  }

  @Test
  fun `async lift callback resource function`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Resource(resourceParent),
      name = Identifier("from-list"),
      transform = FunctionName.Transform.AsyncExportCallback,
    )
    assertThat(function.moduleName).isEqualTo("[callback][async-lift]wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[method]fields.from-list")
  }

  @Test
  fun `async lift callback world function`() {
    val serviceName = "wasi:http/proxy@0.3.0".toServiceName()
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.WorldExport(serviceName),
      name = Identifier("from-list"),
      transform = FunctionName.Transform.AsyncExportCallback,
    )
    assertThat(function.moduleName).isNull()
    assertThat(function.abiName).isEqualTo("[callback][async-lift]from-list")
  }

  @Test
  fun `async lower resource function`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Resource(resourceParent),
      name = Identifier("from-list"),
      transform = FunctionName.Transform.AsyncImport,
    )
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[async-lower][method]fields.from-list")
  }

  @Test
  fun `async lower world function`() {
    val serviceName = "wasi:http/proxy@0.3.0".toServiceName()
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.WorldImport(serviceName),
      name = Identifier("from-list"),
      transform = FunctionName.Transform.AsyncImport,
    )
    assertThat(function.moduleName).isEqualTo($$"$root")
    assertThat(function.abiName).isEqualTo("[async-lower]from-list")
  }

  @Test
  fun `resource constructor`() {
    val function = FunctionName.Constructor(resourceParent)
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[constructor]fields")
  }

  @Test
  fun `resource close`() {
    val function = FunctionName.ResourceClose(resourceParent)
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[dtor]fields")
  }

  @Test
  fun `resource close drop export`() {
    val function = FunctionName.ResourceDropExport(resourceParent)
    assertThat(function.moduleName).isEqualTo("[export]wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[resource-drop]fields")
  }

  @Test
  fun `resource close drop import`() {
    val function = FunctionName.ResourceDropImport(resourceParent)
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[resource-drop]fields")
  }

  @Test
  fun `resource new`() {
    val function = FunctionName.ResourceNew(resourceParent)
    assertThat(function.moduleName).isEqualTo("[export]wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[resource-new]fields")
  }

  @Test
  fun `static function`() {
    val function = FunctionName.Static(resourceParent, Identifier("has"))
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[static]fields.has")
  }

  @Test
  fun `task return resource function`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Resource(resourceParent),
      name = Identifier("from-list"),
      transform = FunctionName.Transform.TaskReturn,
    )
    assertThat(function.moduleName).isEqualTo("[export]wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[task-return][method]fields.from-list")
  }

  @Test
  fun `task return world export function`() {
    val serviceName = "wasi:http/proxy@0.3.0".toServiceName()
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.WorldExport(serviceName),
      name = Identifier("sum"),
      transform = FunctionName.Transform.TaskReturn,
    )
    assertThat(function.moduleName).isEqualTo($$"[export]$root")
    assertThat(function.abiName).isEqualTo("[task-return]sum")
  }

  @Test
  fun `package name with sem ver`() {
    val function = FunctionName.Constructor(resourceParent)
    assertThat(function.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[constructor]fields")
  }
}

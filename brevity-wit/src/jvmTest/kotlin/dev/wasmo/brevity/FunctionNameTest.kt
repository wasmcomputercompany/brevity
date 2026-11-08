package dev.wasmo.brevity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.wasmo.brevity.TypeName.Stream.Cardinality
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
    val function = FunctionName.ResourceDrop(
      type = resourceParent,
      exportPrefix = true,
    )
    assertThat(function.moduleName).isEqualTo("[export]wasi:http/types@0.3.0")
    assertThat(function.abiName).isEqualTo("[resource-drop]fields")
  }

  @Test
  fun `resource close drop import`() {
    val function = FunctionName.ResourceDrop(
      type = resourceParent,
      exportPrefix = false,
    )
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

  @Test
  fun `future and stream new no export`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Interface("wasi:http/types@0.3.0".toServiceName()),
      name = Identifier("get-response"),
    )
    val streamNew = FunctionName.StreamNew(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
    )
    assertThat(streamNew.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamNew.abiName).isEqualTo("[stream-new-0]get-response")

    val futureNew = FunctionName.StreamNew(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Future,
      id = 0,
    )
    assertThat(futureNew.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(futureNew.abiName).isEqualTo("[future-new-0]get-response")
  }

  @Test
  fun `future and stream export`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Interface("wasi:http/types@0.3.0".toServiceName()),
      name = Identifier("get-response"),
    )

    val futureNew = FunctionName.StreamNew(
      base = function,
      exportPrefix = true,
      cardinality = Cardinality.Future,
      id = 0,
    )
    assertThat(futureNew.moduleName).isEqualTo("[export]wasi:http/types@0.3.0")
    assertThat(futureNew.abiName).isEqualTo("[future-new-0]get-response")

    val streamNew = FunctionName.StreamNew(
      base = function,
      exportPrefix = true,
      cardinality = Cardinality.Stream,
      id = 0,
    )
    assertThat(streamNew.moduleName).isEqualTo("[export]wasi:http/types@0.3.0")
    assertThat(streamNew.abiName).isEqualTo("[stream-new-0]get-response")
  }

  @Test
  fun `future and stream read and write`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Interface("wasi:http/types@0.3.0".toServiceName()),
      name = Identifier("get-response"),
    )

    val futureRead = FunctionName.StreamIo(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Future,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(futureRead.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(futureRead.abiName).isEqualTo("[async-lower][future-read-0]get-response")

    val streamRead = FunctionName.StreamIo(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(streamRead.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamRead.abiName).isEqualTo("[async-lower][stream-read-0]get-response")

    val futureWrite = FunctionName.StreamIo(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Future,
      id = 0,
      op = FunctionName.StreamOp.Write,
    )
    assertThat(futureWrite.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(futureWrite.abiName).isEqualTo("[async-lower][future-write-0]get-response")

    val streamWrite = FunctionName.StreamIo(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Write,
    )
    assertThat(streamWrite.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamWrite.abiName).isEqualTo("[async-lower][stream-write-0]get-response")
  }

  @Test
  fun `future and stream cancel read and cancel write`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Interface("wasi:http/types@0.3.0".toServiceName()),
      name = Identifier("get-response"),
    )

    val futureCancelRead = FunctionName.StreamCancel(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Future,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(futureCancelRead.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(futureCancelRead.abiName).isEqualTo("[future-cancel-read-0]get-response")

    val streamCancelRead = FunctionName.StreamCancel(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(streamCancelRead.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamCancelRead.abiName).isEqualTo("[stream-cancel-read-0]get-response")

    val futureCancelWrite = FunctionName.StreamCancel(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Future,
      id = 0,
      op = FunctionName.StreamOp.Write,
    )
    assertThat(futureCancelWrite.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(futureCancelWrite.abiName).isEqualTo("[future-cancel-write-0]get-response")

    val streamCancelWrite = FunctionName.StreamCancel(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Write,
    )
    assertThat(streamCancelWrite.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamCancelWrite.abiName).isEqualTo("[stream-cancel-write-0]get-response")
  }

  @Test
  fun `future and stream drop readable and drop writable`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Interface("wasi:http/types@0.3.0".toServiceName()),
      name = Identifier("get-response"),
    )

    val futureDropReadable = FunctionName.StreamDrop(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Future,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(futureDropReadable.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(futureDropReadable.abiName).isEqualTo("[future-drop-readable-0]get-response")

    val streamDropReadable = FunctionName.StreamDrop(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(streamDropReadable.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamDropReadable.abiName).isEqualTo("[stream-drop-readable-0]get-response")

    val futureDropWritable = FunctionName.StreamDrop(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Future,
      id = 0,
      op = FunctionName.StreamOp.Write,
    )
    assertThat(futureDropWritable.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(futureDropWritable.abiName).isEqualTo("[future-drop-writable-0]get-response")

    val streamDropWritable = FunctionName.StreamDrop(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Write,
    )
    assertThat(streamDropWritable.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamDropWritable.abiName).isEqualTo("[stream-drop-writable-0]get-response")
  }

  @Test
  fun `stream functions on world`() {
    val serviceName = "wasi:http/proxy@0.3.0".toServiceName()
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.WorldExport(serviceName),
      name = Identifier("sum"),
    )

    val streamNew = FunctionName.StreamNew(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
    )
    assertThat(streamNew.moduleName).isNull()
    assertThat(streamNew.abiName).isEqualTo("[stream-new-0]sum")

    val streamRead = FunctionName.StreamIo(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(streamRead.moduleName).isNull()
    assertThat(streamRead.abiName).isEqualTo("[async-lower][stream-read-0]sum")

    val streamCancelRead = FunctionName.StreamCancel(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(streamCancelRead.moduleName).isNull()
    assertThat(streamCancelRead.abiName).isEqualTo("[stream-cancel-read-0]sum")

    val streamDropReadable = FunctionName.StreamDrop(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(streamDropReadable.moduleName).isNull()
    assertThat(streamDropReadable.abiName).isEqualTo("[stream-drop-readable-0]sum")
  }

  @Test
  fun `stream functions on resource`() {
    val function = FunctionName.Standard(
      parent = FunctionName.Parent.Resource(resourceParent),
      name = Identifier("get"),
    )

    val streamNew = FunctionName.StreamNew(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
    )
    assertThat(streamNew.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamNew.abiName).isEqualTo("[stream-new-0][method]fields.get")

    val streamRead = FunctionName.StreamIo(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(streamRead.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamRead.abiName).isEqualTo("[async-lower][stream-read-0][method]fields.get")

    val streamCancelRead = FunctionName.StreamCancel(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(streamCancelRead.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamCancelRead.abiName).isEqualTo("[stream-cancel-read-0][method]fields.get")

    val streamDropReadable = FunctionName.StreamDrop(
      base = function,
      exportPrefix = false,
      cardinality = Cardinality.Stream,
      id = 0,
      op = FunctionName.StreamOp.Read,
    )
    assertThat(streamDropReadable.moduleName).isEqualTo("wasi:http/types@0.3.0")
    assertThat(streamDropReadable.abiName).isEqualTo("[stream-drop-readable-0][method]fields.get")
  }
}

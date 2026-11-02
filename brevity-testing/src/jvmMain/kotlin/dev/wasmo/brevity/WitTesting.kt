@file:OptIn(WitCoreInternalApi::class)

package dev.wasmo.brevity

import dev.wasmo.brevity.io.WitSyntaxReader
import dev.wasmo.brevity.io.toServiceName

fun String.toIdentifier(): Identifier = collectNoIssuesOrThrow {
  val reader = WitSyntaxReader(Location("file.wit"), this@toIdentifier)
  reader.readIdentifier().also {
    check(reader.exhausted)
  }
}

fun String.toPackageName(): PackageName = collectNoIssuesOrThrow {
  val reader = WitSyntaxReader(Location("file.wit"), this@toPackageName)
  reader.readPackageName().also {
    check(reader.exhausted)
  }
}

fun String.toSemVer(): SemVer {
  val reader = WitSyntaxReader(Location("file.wit"), this)
  val result = reader.readSemVer()
  check(reader.exhausted)
  return result
}

fun FunctionNameConstructor(
  serviceName: String,
  name: String,
) = FunctionName.Constructor(
  TypeName.Declared(
    serviceName = serviceName.toServiceName(),
    name = Identifier(name),
  ),
)

fun FunctionNameInterface(
  serviceName: String,
  name: String,
) = FunctionName.Standard(
  parent = FunctionName.Parent.Interface(serviceName.toServiceName()),
  name = Identifier(name),
)

fun FunctionNameMethod(
  serviceName: String,
  name: String,
  resourceName: String,
) = FunctionName.Standard(
  parent = FunctionName.Parent.Resource(
    TypeName.Declared(serviceName.toServiceName(), Identifier(resourceName)),
  ),
  name = Identifier(name),
)

fun FunctionNameStatic(
  serviceName: String,
  name: String,
  resourceName: String,
) = FunctionName.Static(
  type = TypeName.Declared(serviceName.toServiceName(), Identifier(resourceName)),
  name = Identifier(name),
)

fun FunctionNameWorld(
  serviceName: String,
  name: String,
  orientation: Orientation,
) = FunctionName.Standard(
  parent = when (orientation) {
    Orientation.Export -> FunctionName.Parent.WorldExport(serviceName.toServiceName())
    Orientation.Import -> FunctionName.Parent.WorldImport(serviceName.toServiceName())
  },
  name = Identifier(name),
)

fun Gate(
  unstable: String? = null,
  since: String? = null,
  deprecated: String? = null,
) = Gate(
  unstable = unstable?.let { Identifier(it) },
  since = since?.toSemVer(),
  deprecated = deprecated?.toSemVer(),
)

fun ServiceName(
  packageName: String,
  name: String,
) = ServiceName(
  packageName = packageName.toPackageName(),
  name = Identifier(name),
)

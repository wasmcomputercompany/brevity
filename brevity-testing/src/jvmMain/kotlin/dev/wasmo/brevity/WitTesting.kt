@file:OptIn(WitCoreInternalApi::class)

package dev.wasmo.brevity

import dev.wasmo.brevity.io.WitSyntaxReader
import dev.wasmo.brevity.io.toServiceName

fun String.toIdentifier(): Identifier {
  val reader = WitSyntaxReader(this)
  val result = reader.readIdentifier()
  check(reader.exhausted)
  return result
}

fun String.toPackageName(): PackageName {
  val reader = WitSyntaxReader(this)
  val result = reader.readPackageName()
  check(reader.exhausted)
  return result
}

fun String.toSemVer(): SemVer {
  val reader = WitSyntaxReader(this)
  val result = reader.readSemVer()
  check(reader.exhausted)
  return result
}

fun FunctionNameConstructor(
  serviceName: String,
  name: String,
) = FunctionName.Constructor(
  serviceName = serviceName.toServiceName(),
  name = Identifier(name),
)

fun FunctionNameResourceDrop(
  serviceName: String,
  resourceName: String,
) = FunctionName.ResourceDrop(
  serviceName = serviceName.toServiceName(),
  resourceName = Identifier(resourceName),
)

fun FunctionNameInterface(
  serviceName: String,
  name: String,
) = FunctionName.Interface(
  serviceName = serviceName.toServiceName(),
  name = Identifier(name),
)

fun FunctionNameMethod(
  serviceName: String,
  name: String,
  resourceName: String,
) = FunctionName.Method(
  serviceName = serviceName.toServiceName(),
  name = Identifier(name),
  resourceName = Identifier(resourceName),
)

fun FunctionNameStatic(
  serviceName: String,
  name: String,
  resourceName: String,
) = FunctionName.Static(
  serviceName = serviceName.toServiceName(),
  name = Identifier(name),
  resourceName = Identifier(resourceName),
)

fun FunctionNameWorld(
  name: String,
) = FunctionName.World(
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

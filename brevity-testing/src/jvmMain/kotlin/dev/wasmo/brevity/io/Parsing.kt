@file:OptIn(WitCoreInternalApi::class)

package dev.wasmo.brevity.io

import dev.wasmo.brevity.Location
import dev.wasmo.brevity.IoServiceName
import dev.wasmo.brevity.WitCoreInternalApi
import dev.wasmo.brevity.collectNoIssuesOrThrow

fun String.toIoTypeName(): IoTypeName = collectNoIssuesOrThrow {
  val reader = WitSyntaxReader(Location("file.wit"), this@toIoTypeName)
  reader.readTypeName().also {
    check(reader.exhausted)
  }
}

fun String.toUsePath(): UsePath = collectNoIssuesOrThrow {
  val reader = WitSyntaxReader(Location("file.wit"), this@toUsePath)
  reader.readUsePath().also {
    check(reader.exhausted)
  }
}

fun String.toServiceName(): IoServiceName {
  val usePath = toUsePath()
  val packageName = usePath.packageName ?: error("expected a fully-qualified service name")
  return IoServiceName(packageName, usePath.name)
}

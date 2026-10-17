package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier

val FunctionName.importFunctionName: String
  get() = toExternalName(Identifier("import"))

val FunctionName.exportFunctionName: String
  get() = toExternalName(Identifier("export"))

private fun FunctionName.toExternalName(suffix: Identifier): String {
  val segments = segments() + suffix

  return segments.joinToString(separator = "_") {
    it.lowerCamelCase.replace(Regex("\\W"), "_")
  }
}

private fun FunctionName.segments(): List<Identifier> {
  return when (this) {
    is FunctionName.Constructor -> listOf(serviceName.name, name)
    is FunctionName.Interface -> listOf(serviceName.name, name)
    is FunctionName.Method -> listOf(serviceName.name, resourceName, name)
    is FunctionName.ResourceNew -> listOf(resourceType.serviceName.name, resourceType.name, resourceNewSuffix)
    is FunctionName.ResourceClose -> listOf(resourceType.serviceName.name, resourceType.name, resourceCloseName)
    is FunctionName.ResourceDrop -> listOf(resourceType.serviceName.name, resourceType.name, resourceDropSuffix)
    is FunctionName.Static -> listOf(serviceName.name, resourceName, name)
    is FunctionName.World -> listOf(name)
    is FunctionName.TaskReturn -> original.segments() + taskReturnSuffix
    is FunctionName.AsyncExport -> original.segments() + asyncLiftSuffix
    is FunctionName.AsyncExportCallback -> original.segments() + asyncLiftCallbackSuffix
    is FunctionName.AsyncImport -> original.segments() + asyncLowerSuffix
  }
}

val taskReturnSuffix = Identifier("task-return")
val asyncLiftSuffix = Identifier("async-lift")
val asyncLowerSuffix = Identifier("async-lower")
val asyncLiftCallbackSuffix = Identifier("async-callback")
val resourceNewSuffix = Identifier("resource-new")
val resourceCloseName = Identifier("close")
val resourceDropSuffix = Identifier("resource-drop")

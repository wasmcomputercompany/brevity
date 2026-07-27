package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier

val FunctionName.importFunctionName: String
  get() = toExternalName(Identifier("import"))

val FunctionName.exportFunctionName: String
  get() = toExternalName(Identifier("export"))

private fun FunctionName.toExternalName(suffix: Identifier): String {
  val segments = when (this) {
    is FunctionName.Constructor -> listOf(serviceName.name, name, suffix)
    is FunctionName.Interface -> listOf(serviceName.name, name, suffix)
    is FunctionName.Method -> listOf(serviceName.name, resourceName, name, suffix)
    is FunctionName.ResourceDrop -> listOf(serviceName.name, resourceName, dropFunctionName, suffix)
    is FunctionName.Static -> listOf(serviceName.name, resourceName, name, suffix)
    is FunctionName.World -> listOf(name, suffix)
  }

  return segments.joinToString(separator = "_") {
    it.toCamelCase(upperCamel = false).replace(Regex("\\W"), "_")
  }
}

val dropFunctionName = Identifier("close")

package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier

val FunctionName.importFunctionName: String
  get() = toExternalName(Identifier("import"))

val FunctionName.exportFunctionName: String
  get() = toExternalName(Identifier("export"))

private fun FunctionName.toExternalName(suffix: Identifier): String {
  val segments = buildList {
    add(serviceName?.name)
    add(resourceName)
    add(name)
    add(suffix)
  }.filterNotNull()

  return segments.joinToString(separator = "_") {
    it.toCamelCase(upperCamel = false).replace(Regex("\\W"), "_")
  }
}

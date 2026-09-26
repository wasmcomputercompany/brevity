package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.ir.IrWorld

val FunctionName.importFunctionName: String
  get() = toExternalName(Identifier("import"))

val FunctionName.exportFunctionName: String
  get() = toExternalName(Identifier("export"))

val IrWorld.retainWasmExportsFunctionName: String
  get() = "retainWasmExportsFor${serviceName.name.upperCamelCase}"

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
    is FunctionName.ResourceDrop -> listOf(serviceName.name, resourceName, dropFunctionName)
    is FunctionName.Static -> listOf(serviceName.name, resourceName, name)
    is FunctionName.World -> listOf(name)
    is FunctionName.TaskReturn -> original.segments() + taskReturnSuffix
    is FunctionName.AsyncLift -> original.segments() + asyncLiftSuffix
    is FunctionName.AsyncLiftCallback -> original.segments() + asyncLiftCallbackSuffix
  }
}

val dropFunctionName = Identifier("close")
val taskReturnSuffix = Identifier("task-return")
val asyncLiftSuffix = Identifier("async")
val asyncLiftCallbackSuffix = Identifier("async-callback")

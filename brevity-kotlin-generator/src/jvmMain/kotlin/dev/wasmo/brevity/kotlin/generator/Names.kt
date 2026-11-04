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
  return buildList {
    val parent = parent
    add(parent.serviceName.name)
    if (parent is FunctionName.Parent.Resource) {
      add(parent.type.name)
    }
    when (this@segments) {
      is FunctionName.Standard -> add(name)
      is FunctionName.Static -> add(name)
      is FunctionName.Constructor -> {}
      is FunctionName.ResourceClose -> add(resourceCloseName)
      is FunctionName.ResourceDrop -> add(resourceDropSuffix)
      is FunctionName.ResourceNew -> add(resourceNewSuffix)
      is FunctionName.StreamNew -> add(Identifier("${base.name}-stream-new$id"))
      is FunctionName.StreamIo -> add(Identifier("${base.name}-stream-${op.verb}$id"))
      is FunctionName.StreamCancel -> add(Identifier("${base.name}-stream-cancel-${op.verb}$id"))
      is FunctionName.StreamDrop -> add(Identifier("${base.name}-stream-drop-${op.noun}$id"))
    }
    when (transform) {
      FunctionName.Transform.AsyncExport -> add(asyncLiftSuffix)
      FunctionName.Transform.AsyncExportCallback -> add(asyncLiftCallbackSuffix)
      FunctionName.Transform.AsyncImport -> add(asyncLowerSuffix)
      FunctionName.Transform.TaskReturn -> add(taskReturnSuffix)
      null -> {}
    }
  }
}

val taskReturnSuffix = Identifier("task-return")
val asyncLiftSuffix = Identifier("async-lift")
val asyncLowerSuffix = Identifier("async-lower")
val asyncLiftCallbackSuffix = Identifier("async-callback")
val resourceNewSuffix = Identifier("resource-new")
val resourceCloseName = Identifier("close")
val resourceDropSuffix = Identifier("resource-drop")

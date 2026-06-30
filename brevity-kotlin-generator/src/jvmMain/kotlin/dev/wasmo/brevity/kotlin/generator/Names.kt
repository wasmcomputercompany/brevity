package dev.wasmo.brevity.kotlin.generator

import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier

val FunctionName.fullyQualifiedKotlinName: String
  get() {
    val segments = buildList {
      val packageName = packageName
      if (packageName != null) {
        addAll(packageName.namespaces)
        addAll(packageName.names)
        add(packageName.version?.let { Identifier("v${it.version}") })
      }
      add(parentName)
      add(resourceName)
      add(name)
    }.filterNotNull()

    return segments.joinToString(separator = "_") {
      it.toCamelCase(upperCamel = false).replace(Regex("\\W"), "_")
    }
  }

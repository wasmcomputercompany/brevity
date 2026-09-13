package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.PackageName
import dev.wasmo.brevity.ServiceName

val PackageName.kotlinApi: String
  get() {
    val segments = buildList {
      add(kotlinPackagePrefix)
      addAll(namespaces.map { it.packageCase })
      addAll(names.map { it.packageCase })
      version?.let {
        add("v${it.version.toPackageSegment()}")
      }
    }
    return segments.joinToString(separator = ".")
  }

private fun String.toPackageSegment(): String {
  return map { char ->
    when (char) {
      in '0'..'9' -> char
      in 'a'..'z' -> char
      in 'A'..'Z' -> char - ('A' - 'a')
      else -> '_'
    }
  }.toCharArray().concatToString()
}

val ServiceName.kotlinApi: ClassName
  get() = ClassName(packageName.kotlinApi, name.upperCamelCase)

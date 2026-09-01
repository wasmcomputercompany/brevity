package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.IoIdentifier
import dev.wasmo.brevity.PackageName

/** Maps type names in WIT to type names in Kotlin. */
sealed interface KotlinName {
  /** Appends [identifier] to the end of this name. */
  operator fun plus(identifier: IoIdentifier): Class

  class Package(
    val name: String,
  ) : KotlinName {
    override fun plus(identifier: IoIdentifier) =
      Class(ClassName(name, identifier.upperCamelCase))
  }

  class Class(
    val name: ClassName,
  ) : KotlinName {
    override fun plus(identifier: IoIdentifier) =
      Class(name.nestedClass(identifier.upperCamelCase))
  }
}

fun PackageName.toKotlin(): KotlinName.Package {
  val segments = buildList {
    add(kotlinPackagePrefix)
    addAll(namespaces.map { it.packageCase })
    addAll(names.map { it.packageCase })
    version?.let {
      add("v${it.version.toPackageSegment()}")
    }
  }
  return KotlinName.Package(segments.joinToString(separator = "."))
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

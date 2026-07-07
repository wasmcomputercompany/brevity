package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.PackageName

/** Maps type names in WIT to type names in Kotlin. */
sealed interface KotlinName {
  /** Appends [identifier] to the end of this name. */
  operator fun plus(identifier: Identifier): Class

  class Package(
    val name: String,
  ) : KotlinName {
    override fun plus(identifier: Identifier) =
      Class(ClassName(name, identifier.toCamelCase(upperCamel = true)))
  }

  class Class(
    val name: ClassName,
  ) : KotlinName {
    override fun plus(identifier: Identifier) =
      Class(name.nestedClass(identifier.toCamelCase(upperCamel = true)))
  }
}

fun PackageName.toKotlin(): KotlinName.Package {
  val segments = buildList {
    add(kotlinPackagePrefix)
    addAll(namespaces.map { it.name.toPackageSegment() })
    addAll(names.map { it.name.toPackageSegment() })
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

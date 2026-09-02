package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.wasmo.brevity.Location
import dev.wasmo.brevity.kotlin.generator.QualifiedSpec.SourceSet

/**
 * A generated KotlinPoet symbol (function, property, or type), plus its location in the source
 * tree.
 *
 * This model allows us to generate symbols as we encounter them in the source model, independent of
 * how they're nested in the completed source tree.
 *
 * Use [ProjectSpec] to turn the flat list of declarations into a structured tree.
 */
sealed interface QualifiedSpec {
  val parent: Parent
  val locations: Set<Location>
  val optIns: Set<ClassName>

  data class Function(
    override val parent: Parent,
    override val locations: Set<Location> = setOf(),
    override val optIns: Set<ClassName> = setOf(),
    val function: FunSpec,
  ) : QualifiedSpec

  data class Property(
    override val parent: Parent,
    override val locations: Set<Location> = setOf(),
    override val optIns: Set<ClassName> = setOf(),
    val property: PropertySpec,
  ) : QualifiedSpec

  data class Type(
    override val parent: Parent,
    override val locations: Set<Location> = setOf(),
    override val optIns: Set<ClassName> = setOf(),
    val className: ClassName,
    val type: TypeSpec,
  ) : QualifiedSpec

  enum class SourceSet {
    CommonMain,
    WasmWasiMain,
    JvmMain,
  }

  sealed interface Parent {
    val sourceSet: SourceSet

    data class Type(
      override val sourceSet: SourceSet,
      val className: ClassName,
    ) : Parent

    data class File(
      override val sourceSet: SourceSet,
      val packageName: String,
      val fileName: String,
    ) : Parent
  }
}

interface QualifiedSpecCollector {
  fun addFunction(function: FunSpec)

  fun addProperty(property: PropertySpec)

  /** Adds [type] with the fully-qualified name [className]. */
  fun addType(className: ClassName, type: TypeSpec)
}

fun MutableList<QualifiedSpec>.collect(
  sourceSet: SourceSet,
  locations: Set<Location>,
  optIns: Set<ClassName> = setOf(),
  packageName: String,
  fileName: String,
  block: QualifiedSpecCollector.() -> Unit,
) {
  val parentFile = QualifiedSpec.Parent.File(sourceSet, packageName, fileName)

  val collector = object : QualifiedSpecCollector {
    override fun addFunction(function: FunSpec) {
      this@collect += QualifiedSpec.Function(
        parent = parentFile,
        locations = locations,
        optIns = optIns,
        function = function,
      )
    }

    override fun addProperty(property: PropertySpec) {
      this@collect += QualifiedSpec.Property(
        parent = parentFile,
        locations = locations,
        optIns = optIns,
        property = property,
      )
    }

    override fun addType(className: ClassName, type: TypeSpec) {
      val parent = className.enclosingClassName()
        ?.let { QualifiedSpec.Parent.Type(sourceSet, it) }
        ?: parentFile
      this@collect += QualifiedSpec.Type(
        parent = parent,
        locations = locations,
        optIns = optIns,
        className = className,
        type = type,
      )
    }
  }

  collector.block()
}

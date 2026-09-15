package dev.wasmo.brevity.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName as KtTypeName
import com.squareup.kotlinpoet.TypeSpec
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.generator.QualifiedSpec
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.kotlinApi
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase
import dev.wasmo.brevity.kotlin.generator.upperCamelCase

/**
 * Maps WIT types to Kotlin types.
 *
 * This accepts custom mappings to override the default behavior.
 */
class KotlinMapper(
  val customTypeMappings: Map<TypeName.Declared, KtTypeName> = mapOf(),
) {
  /** Returns the Kotlin type for [name], honoring any custom type mappings that exist. */
  fun get(name: TypeName.Declared): KtTypeName {
    val mapping = customTypeMappings[name]
    if (mapping != null) return mapping

    return getAbiClassName(name)
  }

  /**
   * Returns the generated Application Binary Interface (ABI) class for [name], which is nested in 
   * its enclosing world or interface.
   *
   * If the declared type's enclosing service is named 'types', the declared type is promoted to the
   * enclosing package.
   */
  fun getAbiClassName(name: TypeName.Declared): ClassName {
    val serviceName = name.serviceName.kotlinApi
    return when {
      name.serviceName.name.name == "types" -> serviceName.peerClass(name.name.upperCamelCase)
      else -> serviceName.nestedClass(name.name.upperCamelCase)
    }
  }

  /** Map WIT types to Kotlin types. */
  fun get(name: TypeName): KtTypeName {
    val mapping = customTypeMappings[name]
    if (mapping != null) return mapping

    return when (name) {
      TypeName.Bool -> Symbols.Kotlin.Boolean
      TypeName.S8 -> Symbols.Kotlin.Byte
      TypeName.S16 -> Symbols.Kotlin.Short
      TypeName.S32 -> Symbols.Kotlin.Int
      TypeName.S64 -> Symbols.Kotlin.Long
      TypeName.U8 -> Symbols.Kotlin.UByte
      TypeName.U16 -> Symbols.Kotlin.UShort
      TypeName.U32 -> Symbols.Kotlin.UInt
      TypeName.U64 -> Symbols.Kotlin.ULong
      TypeName.F32 -> Symbols.Kotlin.Float
      TypeName.F64 -> Symbols.Kotlin.Double
      TypeName.Char -> Symbols.Kotlin.Int
      TypeName.String -> Symbols.Kotlin.String
      is TypeName.Borrow -> get(name.type)
      is TypeName.Future -> Symbols.KotlinCoroutines.Deferred.parameterizedBy(
        name.type?.let { get(it) } ?: STAR,
      )

      is TypeName.List if name.size != null -> {
        LIST.parameterizedBy(get(name.type))
      }

      is TypeName.List -> when (name.type) {
        TypeName.Bool -> Symbols.Kotlin.BooleanArray
        TypeName.S8 -> Symbols.Okio.ByteString
        TypeName.S16 -> Symbols.Kotlin.ShortArray
        TypeName.S32 -> Symbols.Kotlin.IntArray
        TypeName.S64 -> Symbols.Kotlin.LongArray
        TypeName.U8 -> Symbols.Okio.ByteString
        TypeName.U16 -> Symbols.Kotlin.UShortArray
        TypeName.U32 -> Symbols.Kotlin.UIntArray
        TypeName.U64 -> Symbols.Kotlin.ULongArray
        TypeName.F32 -> Symbols.Kotlin.FloatArray
        TypeName.F64 -> Symbols.Kotlin.DoubleArray
        else -> LIST.parameterizedBy(get(name.type))
      }

      is TypeName.Map -> Symbols.KotlinCollections.Map.parameterizedBy(
        get(name.key),
        get(name.value),
      )

      is TypeName.Option -> get(name.type).copy(nullable = true)
      is TypeName.Result -> Symbols.Brevity.Result.parameterizedBy(
        name.ok?.let { get(it) } ?: STAR,
        name.error?.let { get(it) } ?: STAR,
      )

      is TypeName.Declared -> get(name)

      is TypeName.Stream -> Symbols.Brevity.Stream.parameterizedBy(
        name.type?.let { get(it) } ?: STAR,
      )

      is TypeName.Tuple -> when (name.types.size) {
        2 -> Symbols.Kotlin.Pair.parameterizedBy(
          *name.types.map { get(it) }.toTypedArray(),
        )

        3 -> Symbols.Kotlin.Triple.parameterizedBy(
          *name.types.map { get(it) }.toTypedArray(),
        )

        4 -> Symbols.Brevity.Quad.parameterizedBy(
          *name.types.map { get(it) }.toTypedArray(),
        )

        else -> {
          val elementType = name.types.toSet().singleOrNull()?.let { get(it) } ?: STAR
          Symbols.KotlinCollections.List.parameterizedBy(elementType)
        }
      }
    }
  }

  /** Returns declarations of the `Adapter` interfaces that perform custom type mappings. */
  fun adapterInterfaces(): List<QualifiedSpec> {
    val result = mutableMapOf<ClassName, TypeSpec.Builder>()

    for ((name, target) in customTypeMappings) {
      val memberName = getAdapterMemberName(name)
      val adaptersClassName = ClassName(memberName.packageName, "Adapters")

      val typeSpecBuilder = result.getOrPut(adaptersClassName) {
        TypeSpec.interfaceBuilder(adaptersClassName)
          .addModifiers(KModifier.INTERNAL)
          .addKdoc(
            """
            |Declare an object named `RealAdapters` in this module's commonMain.
            |The object will be used by generated code to convert ABI types to
            |API types.
            |
            |```
            |package ${memberName.packageName}
            |
            |internal object RealAdapters : Adapters {
            |  ...
            |}
            |```
            """.trimMargin()
          )
      }

      typeSpecBuilder.addProperty(
        PropertySpec.builder(
          memberName.simpleName,
          Symbols.Brevity.WitAdapter.parameterizedBy(
            getAbiClassName(name),
            target,
          )
        ).build()
      )
    }

    return result.map { (className, typeSpecBuilder) ->
      QualifiedSpec.Type(
        parent = QualifiedSpec.Parent.File(
          sourceSet = QualifiedSpec.SourceSet.CommonMain,
          packageName = className.packageName,
          fileName = className.simpleName,
        ),
        className = className,
        type = typeSpecBuilder.build()
      )
    }
  }

  fun getAdapterMemberName(name: TypeName.Declared): MemberName =
    ClassName(getAbiClassName(name).packageName, "RealAdapters")
      .member("${name.serviceName.name.lowerCamelCase}${name.name.upperCamelCase}")
}

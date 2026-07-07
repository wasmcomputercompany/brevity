package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.Annotation
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.ir.IrTypeName

val ServiceName.kotlinApi: ClassName
  get() = (packageName.toKotlin() + name).name

val IrTypeName.kotlinAbi: TypeName
  get() = INT

/** Returns true if we've done the work to implement this. */
val KtFunction.isSupported: Boolean
  get() = name.annotation == null ||
    name.annotation == Annotation.Method ||
    name.annotation == Annotation.ResourceDrop

private val specialCases = mapOf(
  IrTypeName.List(IrTypeName.S8) to ClassName("kotlin", "ByteArray"),
  IrTypeName.List(IrTypeName.S16) to ClassName("kotlin", "ShortArray"),
  IrTypeName.List(IrTypeName.S32) to ClassName("kotlin", "IntArray"),
  IrTypeName.List(IrTypeName.S64) to ClassName("kotlin", "LongAray"),
  IrTypeName.List(IrTypeName.U8) to ClassName("kotlin", "UByteArray"),
  IrTypeName.List(IrTypeName.U16) to ClassName("kotlin", "UShortArray"),
  IrTypeName.List(IrTypeName.U32) to ClassName("kotlin", "UIntArray"),
  IrTypeName.List(IrTypeName.U64) to ClassName("kotlin", "ULongAray"),
  IrTypeName.List(IrTypeName.F32) to ClassName("kotlin", "FloatArray"),
  IrTypeName.List(IrTypeName.F64) to ClassName("kotlin", "DoubleArray"),
)

val IrTypeName.Declared.kotlinApi: ClassName
  get() = serviceName.kotlinApi.nestedClass(name.toCamelCase(upperCamel = true))

/** Map WIT types to Kotlin types. */
val IrTypeName.kotlinApi: TypeName
  get() {
    val specialCase = specialCases[this]
    if (specialCase != null) return specialCase

    return when (this) {
      IrTypeName.Bool -> Symbols.Kotlin.Boolean
      IrTypeName.S8 -> Symbols.Kotlin.Byte
      IrTypeName.S16 -> Symbols.Kotlin.Short
      IrTypeName.S32 -> Symbols.Kotlin.Int
      IrTypeName.S64 -> Symbols.Kotlin.Long
      IrTypeName.U8 -> Symbols.Kotlin.UByte
      IrTypeName.U16 -> Symbols.Kotlin.UShort
      IrTypeName.U32 -> Symbols.Kotlin.UInt
      IrTypeName.U64 -> Symbols.Kotlin.ULong
      IrTypeName.F32 -> Symbols.Kotlin.Float
      IrTypeName.F64 -> Symbols.Kotlin.Double
      IrTypeName.Char -> Symbols.Kotlin.Int
      IrTypeName.String -> Symbols.Kotlin.String
      is IrTypeName.Borrow -> type.kotlinApi
      is IrTypeName.Future -> Symbols.KotlinCoroutines.Deferred.parameterizedBy(
        type?.kotlinApi ?: STAR,
      )

      is IrTypeName.List -> LIST.parameterizedBy(type.kotlinApi)
      is IrTypeName.Map -> Symbols.KotlinCollections.Map.parameterizedBy(
        key.kotlinApi,
        value.kotlinApi,
      )

      is IrTypeName.Option -> type.kotlinApi.copy(nullable = true)
      is IrTypeName.Result -> Symbols.Kotlin.Pair.parameterizedBy(
        ok?.kotlinApi ?: STAR,
        err?.kotlinApi ?: STAR,
      )

      is IrTypeName.Declared -> kotlinApi

      is IrTypeName.Stream -> Symbols.Brevity.Stream.parameterizedBy(
        type?.kotlinApi ?: STAR,
      )

      is IrTypeName.Tuple -> when (types.size) {
        2 -> Symbols.Kotlin.Pair.parameterizedBy(*types.map { it.kotlinApi }.toTypedArray())
        3 -> Symbols.Kotlin.Triple.parameterizedBy(*types.map { it.kotlinApi }.toTypedArray())
        4 -> Symbols.Brevity.Quad.parameterizedBy(*types.map { it.kotlinApi }.toTypedArray())
        else -> Symbols.KotlinCollections.List.parameterizedBy(STAR)
      }
    }
  }

val KtResource.handleName: ClassName
  get() = ClassName(className.packageName, "${className.simpleName}Handle")

val IrTypeName.Declared.handleName: ClassName
  get() = ClassName(kotlinApi.packageName, "${kotlinApi.simpleName}Handle")

const val kotlinPackagePrefix: String = "wit"

package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.Annotation
import dev.wasmo.brevity.ServiceName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrCase
import dev.wasmo.brevity.ir.IrExternalApi
import dev.wasmo.brevity.ir.IrField
import dev.wasmo.brevity.ir.IrFlag
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrParameter

const val kotlinPackagePrefix: String = "wit"

val IrCase.kotlinName: String
  get() = name.toCamelCase(upperCamel = true)

val IrParameter.kotlinName: String
  get() = name.toCamelCase(upperCamel = false)

val IrField.kotlinName: String
  get() = name.toCamelCase(upperCamel = false)

val IrFlag.kotlinName: String
  get() = name.toCamelCase(upperCamel = false)

val IrFunction.kotlinName: String
  get() = functionName.name.toCamelCase(upperCamel = false)

val IrExternalApi.instanceName: String
  get() = (plainName ?: serviceName.name).toCamelCase(upperCamel = false)

val ServiceName.kotlinApi: ClassName
  get() = (packageName.toKotlin() + name).name

val ServiceName.bridgeType: ClassName
  get() = ClassName(
    kotlinApi.packageName,
    "Bridge${name.toCamelCase(upperCamel = true)}",
  )

val TypeName.kotlinAbi: KtTypeName
  get() = when (this) {
    TypeName.S64 -> LONG
    else -> INT
  }

val TypeName.Declared.handleName: ClassName
  get() = ClassName(kotlinApi.packageName, "${kotlinApi.simpleName}Handle")

private val specialCases = mapOf(
  TypeName.List(TypeName.S8) to ClassName("kotlin", "ByteArray"),
  TypeName.List(TypeName.S16) to ClassName("kotlin", "ShortArray"),
  TypeName.List(TypeName.S32) to ClassName("kotlin", "IntArray"),
  TypeName.List(TypeName.S64) to ClassName("kotlin", "LongAray"),
  TypeName.List(TypeName.U8) to ClassName("kotlin", "UByteArray"),
  TypeName.List(TypeName.U16) to ClassName("kotlin", "UShortArray"),
  TypeName.List(TypeName.U32) to ClassName("kotlin", "UIntArray"),
  TypeName.List(TypeName.U64) to ClassName("kotlin", "ULongAray"),
  TypeName.List(TypeName.F32) to ClassName("kotlin", "FloatArray"),
  TypeName.List(TypeName.F64) to ClassName("kotlin", "DoubleArray"),
)

val TypeName.Declared.kotlinApi: ClassName
  get() = serviceName.kotlinApi.nestedClass(name.toCamelCase(upperCamel = true))

/** Map WIT types to Kotlin types. */
val TypeName.kotlinApi: KtTypeName
  get() {
    val specialCase = specialCases[this]
    if (specialCase != null) return specialCase

    return when (this) {
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
      is TypeName.Borrow -> type.kotlinApi
      is TypeName.Future -> Symbols.KotlinCoroutines.Deferred.parameterizedBy(
        type?.kotlinApi ?: STAR,
      )

      is TypeName.List -> LIST.parameterizedBy(type.kotlinApi)
      is TypeName.Map -> Symbols.KotlinCollections.Map.parameterizedBy(
        key.kotlinApi,
        value.kotlinApi,
      )

      is TypeName.Option -> type.kotlinApi.copy(nullable = true)
      is TypeName.Result -> Symbols.Kotlin.Pair.parameterizedBy(
        ok?.kotlinApi ?: STAR,
        err?.kotlinApi ?: STAR,
      )

      is TypeName.Declared -> kotlinApi

      is TypeName.Stream -> Symbols.Brevity.Stream.parameterizedBy(
        type?.kotlinApi ?: STAR,
      )

      is TypeName.Tuple -> when (types.size) {
        2 -> Symbols.Kotlin.Pair.parameterizedBy(*types.map { it.kotlinApi }.toTypedArray())
        3 -> Symbols.Kotlin.Triple.parameterizedBy(*types.map { it.kotlinApi }.toTypedArray())
        4 -> Symbols.Brevity.Quad.parameterizedBy(*types.map { it.kotlinApi }.toTypedArray())
        else -> Symbols.KotlinCollections.List.parameterizedBy(STAR)
      }
    }
  }

/** Returns true if we've done the work to implement this. */
val IrFunction.isSupported: Boolean
  get() = functionName.annotation == null ||
    functionName.annotation == Annotation.Method ||
    functionName.annotation == Annotation.ResourceDrop


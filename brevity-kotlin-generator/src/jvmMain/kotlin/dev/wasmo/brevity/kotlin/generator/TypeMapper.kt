package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import dev.wasmo.brevity.ir.IrParentName
import dev.wasmo.brevity.ir.IrTypeName
import dev.wasmo.brevity.kotlin.generator.KtTypeName.Borrow
import dev.wasmo.brevity.kotlin.generator.KtTypeName.Declared
import dev.wasmo.brevity.kotlin.generator.KtTypeName.Future
import dev.wasmo.brevity.kotlin.generator.KtTypeName.List
import dev.wasmo.brevity.kotlin.generator.KtTypeName.Map
import dev.wasmo.brevity.kotlin.generator.KtTypeName.Option
import dev.wasmo.brevity.kotlin.generator.KtTypeName.Result
import dev.wasmo.brevity.kotlin.generator.KtTypeName.Stream
import dev.wasmo.brevity.kotlin.generator.KtTypeName.Tuple

/**
 * Map WIT types to Kotlin types.
 */
class TypeMapper(
  private val kotlinPackagePrefix: String,
) {
  private val specialCases = mapOf(
    IrTypeName.List(IrTypeName.S8) to KtTypeName.Simple(ClassName("kotlin", "ByteArray"), INT),
    IrTypeName.List(IrTypeName.S16) to KtTypeName.Simple(ClassName("kotlin", "ShortArray"), INT),
    IrTypeName.List(IrTypeName.S32) to KtTypeName.Simple(ClassName("kotlin", "IntArray"), INT),
    IrTypeName.List(IrTypeName.S64) to KtTypeName.Simple(ClassName("kotlin", "LongAray"), INT),
    IrTypeName.List(IrTypeName.U8) to KtTypeName.Simple(ClassName("kotlin", "UByteArray"), INT),
    IrTypeName.List(IrTypeName.U16) to KtTypeName.Simple(ClassName("kotlin", "UShortArray"), INT),
    IrTypeName.List(IrTypeName.U32) to KtTypeName.Simple(ClassName("kotlin", "UIntArray"), INT),
    IrTypeName.List(IrTypeName.U64) to KtTypeName.Simple(ClassName("kotlin", "ULongAray"), INT),
    IrTypeName.List(IrTypeName.F32) to KtTypeName.Simple(ClassName("kotlin", "FloatArray"), INT),
    IrTypeName.List(IrTypeName.F64) to KtTypeName.Simple(ClassName("kotlin", "DoubleArray"), INT),
  )

  fun map(typeName: IrParentName): ClassName {
    return (typeName.packageName.toKotlin(kotlinPackagePrefix) + typeName.name).name
  }

  fun map(typeName: IrTypeName): KtTypeName {
    val specialCase = specialCases[typeName]
    if (specialCase != null) return specialCase

    return when (typeName) {
      IrTypeName.Bool -> KtTypeName.Simple(Symbols.Kotlin.Boolean, INT)
      IrTypeName.S8 -> KtTypeName.Simple(Symbols.Kotlin.Byte, INT)
      IrTypeName.S16 -> KtTypeName.Simple(Symbols.Kotlin.Short, INT)
      IrTypeName.S32 -> KtTypeName.Simple(Symbols.Kotlin.Int, INT)
      IrTypeName.S64 -> KtTypeName.Simple(Symbols.Kotlin.Long, LONG)
      IrTypeName.U8 -> KtTypeName.Simple(Symbols.Kotlin.UByte, INT)
      IrTypeName.U16 -> KtTypeName.Simple(Symbols.Kotlin.UShort, INT)
      IrTypeName.U32 -> KtTypeName.Simple(Symbols.Kotlin.UInt, INT)
      IrTypeName.U64 -> KtTypeName.Simple(Symbols.Kotlin.ULong, LONG)
      IrTypeName.F32 -> KtTypeName.Simple(Symbols.Kotlin.Float, INT)
      IrTypeName.F64 -> KtTypeName.Simple(Symbols.Kotlin.Double, LONG)
      IrTypeName.Char -> KtTypeName.Simple(Symbols.Kotlin.Int, INT)
      IrTypeName.String -> KtTypeName.Simple(Symbols.Kotlin.String, INT)
      is IrTypeName.Borrow -> Borrow(map(typeName.type))
      is IrTypeName.Future -> Future(typeName.type?.let { map(it) })
      is IrTypeName.List -> List(map(typeName.type))
      is IrTypeName.Map -> Map(map(typeName.key), map(typeName.value))
      is IrTypeName.Option -> Option(map(typeName.type))
      is IrTypeName.Result -> Result(
        typeName.ok?.let { map(it) },
        typeName.err?.let { map(it) },
      )

      is IrTypeName.Declared -> {
        Declared(
          apiType = typeName.toKotlin(kotlinPackagePrefix).name,
          codec = when (val codec = typeName.codec) {
            is IrTypeName.Declared.Codec.Alias -> Declared.Codec.Alias(map(codec.target))
            IrTypeName.Declared.Codec.Enum -> Declared.Codec.Enum
            IrTypeName.Declared.Codec.Flags -> Declared.Codec.Flags
            IrTypeName.Declared.Codec.Record -> Declared.Codec.Record
            IrTypeName.Declared.Codec.Resource -> Declared.Codec.Resource
            IrTypeName.Declared.Codec.Variant -> Declared.Codec.Variant
          },
        )
      }

      is IrTypeName.Stream -> Stream(typeName.type?.let { map(it) })
      is IrTypeName.Tuple -> Tuple(typeName.types.map { map(it) })
    }
  }
}

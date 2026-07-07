package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import dev.wasmo.brevity.ServiceName
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
class TypeMapper {
  private val specialCases = mapOf(
    IrTypeName.List(IrTypeName.S8) to KtTypeName.Simple(IrTypeName.List(IrTypeName.S8), ClassName("kotlin", "ByteArray"), INT),
    IrTypeName.List(IrTypeName.S16) to KtTypeName.Simple(IrTypeName.List(IrTypeName.S16), ClassName("kotlin", "ShortArray"), INT),
    IrTypeName.List(IrTypeName.S32) to KtTypeName.Simple(IrTypeName.List(IrTypeName.S32), ClassName("kotlin", "IntArray"), INT),
    IrTypeName.List(IrTypeName.S64) to KtTypeName.Simple(IrTypeName.List(IrTypeName.S64), ClassName("kotlin", "LongAray"), INT),
    IrTypeName.List(IrTypeName.U8) to KtTypeName.Simple(IrTypeName.List(IrTypeName.U8), ClassName("kotlin", "UByteArray"), INT),
    IrTypeName.List(IrTypeName.U16) to KtTypeName.Simple(IrTypeName.List(IrTypeName.U16), ClassName("kotlin", "UShortArray"), INT),
    IrTypeName.List(IrTypeName.U32) to KtTypeName.Simple(IrTypeName.List(IrTypeName.U32), ClassName("kotlin", "UIntArray"), INT),
    IrTypeName.List(IrTypeName.U64) to KtTypeName.Simple(IrTypeName.List(IrTypeName.U64), ClassName("kotlin", "ULongAray"), INT),
    IrTypeName.List(IrTypeName.F32) to KtTypeName.Simple(IrTypeName.List(IrTypeName.F32), ClassName("kotlin", "FloatArray"), INT),
    IrTypeName.List(IrTypeName.F64) to KtTypeName.Simple(IrTypeName.List(IrTypeName.F64), ClassName("kotlin", "DoubleArray"), INT),
  )

  fun map(serviceName: ServiceName): ClassName {
    return (serviceName.packageName.toKotlin() + serviceName.name).name
  }

  fun map(typeName: IrTypeName): KtTypeName {
    val specialCase = specialCases[typeName]
    if (specialCase != null) return specialCase

    return when (typeName) {
      IrTypeName.Bool -> KtTypeName.Simple(typeName, Symbols.Kotlin.Boolean, INT)
      IrTypeName.S8 -> KtTypeName.Simple(typeName, Symbols.Kotlin.Byte, INT)
      IrTypeName.S16 -> KtTypeName.Simple(typeName, Symbols.Kotlin.Short, INT)
      IrTypeName.S32 -> KtTypeName.Simple(typeName, Symbols.Kotlin.Int, INT)
      IrTypeName.S64 -> KtTypeName.Simple(typeName, Symbols.Kotlin.Long, LONG)
      IrTypeName.U8 -> KtTypeName.Simple(typeName, Symbols.Kotlin.UByte, INT)
      IrTypeName.U16 -> KtTypeName.Simple(typeName, Symbols.Kotlin.UShort, INT)
      IrTypeName.U32 -> KtTypeName.Simple(typeName, Symbols.Kotlin.UInt, INT)
      IrTypeName.U64 -> KtTypeName.Simple(typeName, Symbols.Kotlin.ULong, LONG)
      IrTypeName.F32 -> KtTypeName.Simple(typeName, Symbols.Kotlin.Float, INT)
      IrTypeName.F64 -> KtTypeName.Simple(typeName, Symbols.Kotlin.Double, LONG)
      IrTypeName.Char -> KtTypeName.Simple(typeName, Symbols.Kotlin.Int, INT)
      IrTypeName.String -> KtTypeName.Simple(typeName, Symbols.Kotlin.String, INT)
      is IrTypeName.Borrow -> Borrow(typeName, map(typeName.type))
      is IrTypeName.Future -> Future(typeName, typeName.type?.let { map(it) })
      is IrTypeName.List -> List(typeName, map(typeName.type))
      is IrTypeName.Map -> Map(typeName, map(typeName.key), map(typeName.value))
      is IrTypeName.Option -> Option(typeName, map(typeName.type))
      is IrTypeName.Result -> Result(
        typeName,
        typeName.ok?.let { map(it) },
        typeName.err?.let { map(it) },
      )

      is IrTypeName.Declared -> Declared(
        typeName,
        apiType = typeName.toKotlin().name,
      )

      is IrTypeName.Stream -> Stream(typeName, typeName.type?.let { map(it) })
      is IrTypeName.Tuple -> Tuple(typeName, typeName.types.map { map(it) })
    }
  }
}

const val kotlinPackagePrefix: String = "wit"

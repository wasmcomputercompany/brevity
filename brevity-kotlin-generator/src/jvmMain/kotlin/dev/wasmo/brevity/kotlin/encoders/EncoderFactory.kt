package dev.wasmo.brevity.kotlin.encoders

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrVariant

val MAX_FLAT_PARAMS = 16
val MAX_FLAT_RESULTS = 1

class EncoderFactory(
  private val declarationIndex: DeclarationIndex,
) {
  fun get(typeName: TypeName): Encoder {
    return when (typeName) {
      TypeName.Bool -> BooleanEncoder
      TypeName.S8 -> ByteEncoder
      TypeName.S16 -> ShortEncoder
      TypeName.S32 -> IntEncoder
      TypeName.S64 -> LongEncoder
      TypeName.U8 -> UByteEncoder
      TypeName.U16 -> UShortEncoder
      TypeName.U32 -> UIntEncoder
      TypeName.U64 -> ULongEncoder
      TypeName.F32 -> FloatEncoder
      TypeName.F64 -> DoubleEncoder
      TypeName.Char -> CharEncoder
      TypeName.String -> StringEncoder

      is TypeName.Stream -> FallbackEncoder(typeName, CoreType.I32)
      is TypeName.Tuple -> TupleEncoder(typeName.types.map { element -> get(element) })
      is TypeName.Borrow -> FallbackEncoder(typeName, CoreType.I32)
      is TypeName.Declared -> {
        val declaredType = declarationIndex[typeName]
          ?: error("unexpected type: $typeName")
        when (declaredType) {
          is IrEnum -> FallbackEncoder(typeName, CoreType.I32) // TODO: Variant.
          is IrFlags -> FallbackEncoder(typeName, CoreType.I32)
          is IrRecord -> FallbackEncoder(typeName, CoreType.I32) // TODO: RecordEncoder
          is IrResource -> ResourceEncoder(typeName)
          is IrTypeAlias -> FallbackEncoder(typeName, CoreType.I32) // TODO: target.
          is IrVariant -> FallbackEncoder(typeName, CoreType.I32) // TODO: VariantEncoder
        }
      }

      is TypeName.Future -> FallbackEncoder(typeName, CoreType.I32)
      is TypeName.List -> ListEncoder(typeName)
      is TypeName.Map -> FallbackEncoder(typeName, CoreType.I32) // TODO: List<Tuple>.
      is TypeName.Option -> FallbackEncoder(typeName, CoreType.I32) // TODO: Variant.
      is TypeName.Result -> TupleEncoder(
        listOf(
          typeName.ok?.let { get(it) } ?: FallbackEncoder(TypeName.S32, CoreType.I32),
          typeName.err?.let { get(it) } ?: FallbackEncoder(TypeName.S32, CoreType.I32),
        ),
      )
    }
  }
}

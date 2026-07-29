package dev.wasmo.brevity.kotlin.encoders

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.kotlin.generator.kotlinApi

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
      is TypeName.Tuple -> {
        val fieldEncoders = typeName.types.map { element -> get(element) }
        when (fieldEncoders.size) {
          2 -> PairEncoder(fieldEncoders)
          3 -> TripleEncoder(fieldEncoders)
          4 -> QuadEncoder(fieldEncoders)
          else -> LargeTupleEncoder(fieldEncoders)
        }
      }

      is TypeName.Borrow -> FallbackEncoder(typeName, CoreType.I32)
      is TypeName.Declared -> {
        val declaredType = declarationIndex[typeName]
          ?: error("unexpected type: $typeName")
        when (declaredType) {
          is IrEnum -> EnumEncoder(
            kotlinType = declaredType.type.kotlinApi,
            cases = declaredType.cases,
          )

          is IrFlags -> FallbackEncoder(typeName, CoreType.I32)
          is IrRecord -> FallbackEncoder(typeName, CoreType.I32) // TODO: RecordEncoder
          is IrResource -> ResourceEncoder(typeName)
          is IrTypeAlias -> FallbackEncoder(typeName, CoreType.I32) // TODO: target.
          is IrVariant -> VariantEncoder(
            kotlinType = declaredType.type.kotlinApi,
            cases = declaredType.cases,
            caseEncoders = declaredType.cases.map { case ->
              case.type?.let { get(it) }
            },
          )
        }
      }

      is TypeName.Future -> FallbackEncoder(typeName, CoreType.I32)
      is TypeName.List -> {
        when {
          // TODO: Statically-sized lists.
          typeName.size != null -> FallbackEncoder(typeName, CoreType.I32)

          else -> DynamicListEncoder(
            elementEncoder = get(typeName.type),
            listType = typeName.kotlinApi,
          )
        }
      }

      is TypeName.Map -> FallbackEncoder(typeName, CoreType.I32) // TODO: List<Tuple>.
      is TypeName.Option -> OptionalEncoder(get(typeName.type))
      is TypeName.Result -> PairEncoder(
        listOf(
          typeName.ok?.let { get(it) } ?: FallbackEncoder(TypeName.S32, CoreType.I32),
          typeName.err?.let { get(it) } ?: FallbackEncoder(TypeName.S32, CoreType.I32),
        ),
      )
    }
  }
}

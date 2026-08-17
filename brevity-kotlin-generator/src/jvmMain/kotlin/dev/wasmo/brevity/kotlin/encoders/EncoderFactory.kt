package dev.wasmo.brevity.kotlin.encoders

import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.kotlin.generator.kotlinApi
import dev.wasmo.brevity.kotlin.generator.toCamelCase

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
          else -> {
            val onlyTypeOrNull = typeName.types.toSet().singleOrNull()
            when {
              onlyTypeOrNull != null -> StaticListEncoder(
                size = typeName.types.size,
                elementType = onlyTypeOrNull.kotlinApi,
                elementEncoder = get(onlyTypeOrNull),
              )

              else -> LargeTupleEncoder(
                typeName.types.map { it.kotlinApi },
                fieldEncoders,
              )
            }
          }
        }
      }

      // TODO: runtime support for borrow.
      is TypeName.Borrow -> get(typeName.type)
      is TypeName.Declared -> {
        val declaredType = declarationIndex[typeName]
          ?: error("unexpected type: $typeName")
        CallDeclaredTypeEncoder(declaredType, getImplementationEncoder(declaredType))
      }

      is TypeName.Future -> FallbackEncoder(typeName, CoreType.I32)
      is TypeName.List -> {
        val staticSize = typeName.size
        when {
          staticSize != null -> StaticListEncoder(
            size = staticSize.toInt(),
            elementType = typeName.type.kotlinApi,
            elementEncoder = get(typeName.type),
          )

          else -> DynamicListEncoder(
            elementEncoder = get(typeName.type),
            listType = typeName.kotlinApi,
          )
        }
      }

      is TypeName.Map -> FallbackEncoder(typeName, CoreType.I32) // TODO: List<Tuple>.
      is TypeName.Option -> OptionalEncoder(get(typeName.type))
      is TypeName.Result -> ResultEncoder(
        ok = typeName.ok?.let { it.kotlinApi to get(it) },
        error = typeName.error?.let { it.kotlinApi to get(it) },
      )
    }
  }

  /** Returns an encoder that is used to implement [CallDeclaredTypeEncoder]. */
  fun getImplementationEncoder(type: IrTypeDeclaration): Encoder {
    return when (type) {
      is IrEnum -> EnumEncoder(
        kotlinType = type.type.kotlinApi,
        cases = type.cases,
      )

      is IrFlags -> FallbackEncoder(type.type, CoreType.I32)
      is IrRecord -> RecordEncoder(
        kotlinType = type.type.kotlinApi,
        instanceNameHint = type.name.toCamelCase(upperCamel = false),
        fields = type.fields,
        fieldEncoders = type.fields.map { get(it.type) },
      )

      is IrResource -> ResourceEncoder(type.type)
      is IrTypeAlias -> TypeAliasEncoder(type.type.kotlinApi, get(type.target))
      is IrVariant -> VariantEncoder(
        kotlinType = type.type.kotlinApi,
        cases = type.cases,
        caseEncoders = type.cases.map { case ->
          case.type?.let { get(it) }
        },
      )
    }
  }
}

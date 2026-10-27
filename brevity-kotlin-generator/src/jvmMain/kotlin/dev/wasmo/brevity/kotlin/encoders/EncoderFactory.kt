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
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.lowerCamelCase

class EncoderFactory(
  private val kotlinMapper: KotlinMapper,
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

      is TypeName.Stream -> FallbackEncoder(kotlinMapper, typeName, CoreType.I32)
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
                elementType = kotlinMapper.get(onlyTypeOrNull),
                elementEncoder = get(onlyTypeOrNull),
              )

              else -> LargeTupleEncoder(
                typeName.types.map { kotlinMapper.get(it) },
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
        getDeclaredTypeEncoder(declaredType)
      }

      is TypeName.Future -> FallbackEncoder(kotlinMapper, typeName, CoreType.I32)
      is TypeName.List -> {
        val staticSize = typeName.size
        when {
          staticSize != null -> StaticListEncoder(
            size = staticSize.toInt(),
            elementType = kotlinMapper.get(typeName.type),
            elementEncoder = get(typeName.type),
          )

          typeName.type == TypeName.S8 || typeName.type == TypeName.U8 -> ByteStringEncoder(
            DynamicListEncoder(
              elementEncoder = ByteEncoder,
              listType = Symbols.Kotlin.ByteArray,
            ),
          )

          else -> DynamicListEncoder(
            elementEncoder = get(typeName.type),
            listType = kotlinMapper.get(typeName),
          )
        }
      }

      is TypeName.Map -> MapEncoder(
        keyType = kotlinMapper.get(typeName.key),
        valueType = kotlinMapper.get(typeName.value),
        keyEncoder = get(typeName.key),
        valueEncoder = get(typeName.value),
      )

      is TypeName.Option -> OptionalEncoder(
        type = typeName,
        some = get(typeName.type),
        instanceNameHint = when (val element = typeName.type) {
          is TypeName.Declared -> element.name.lowerCamelCase
          else -> "optional"
        },
      )

      is TypeName.Result -> ResultEncoder(
        type = typeName,
        ok = typeName.ok?.let { kotlinMapper.get(it) to get(it) },
        error = typeName.error?.let { kotlinMapper.get(it) to get(it) },
      )
    }
  }

  private fun getDeclaredTypeEncoder(type: IrTypeDeclaration): Encoder {
    val kotlinType = kotlinMapper.getAbiClassName(type.type)
    return when (type) {
      is IrEnum -> EnumEncoder(
        type = type.type,
        kotlinType = kotlinType,
        instanceNameHint = type.type.name.lowerCamelCase,
        cases = type.cases,
      )

      is IrFlags -> {
        when {
          type.flags.size <= 8 -> FlagsEncoder(
            type = type.type,
            kotlinType = kotlinType,
            flags = type.flags,
            packedFlagEncoder = ByteEncoder,
          )

          type.flags.size <= 16 -> FlagsEncoder(
            type = type.type,
            kotlinType = kotlinType,
            flags = type.flags,
            packedFlagEncoder = ShortEncoder,
          )

          type.flags.size <= 32 -> FlagsEncoder(
            type = type.type,
            kotlinType = kotlinType,
            flags = type.flags,
            packedFlagEncoder = IntEncoder,
          )

          else -> FallbackEncoder(kotlinMapper, type.type, CoreType.I32)
        }
      }

      is IrRecord -> RecordEncoder(
        type = type.type,
        kotlinType = kotlinType,
        instanceNameHint = type.name.lowerCamelCase,
        fields = type.fields,
        fieldEncoders = type.fields.map { get(it.type) },
      )

      is IrResource -> ResourceEncoder(
        abiClassName = kotlinMapper.getAbiClassName(type.type),
        type = type.type,
      )

      is IrTypeAlias -> TypeAliasEncoder(
        type = type.type,
        ktType = kotlinType,
        delegate = get(type.target),
      )
      is IrVariant -> VariantEncoder(
        type = type.type,
        kotlinType = kotlinType,
        instanceNameHint = type.type.name.lowerCamelCase,
        cases = type.cases,
        caseEncoders = type.cases.map { case ->
          case.type?.let { get(it) }
        },
      )
    }
  }
}

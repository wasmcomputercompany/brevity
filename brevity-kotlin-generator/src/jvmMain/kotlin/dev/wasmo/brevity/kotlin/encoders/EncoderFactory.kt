package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrEnum
import dev.wasmo.brevity.ir.IrFlags
import dev.wasmo.brevity.ir.IrRecord
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeAlias
import dev.wasmo.brevity.ir.IrVariant
import dev.wasmo.brevity.kotlin.generator.CoreType

val MAX_FLAT_PARAMS = 16
val MAX_FLAT_RESULTS = 1

class EncoderFactory(
  private val declarationIndex: DeclarationIndex,
) {
  fun get(typeName: TypeName): Encoder {
    return when (typeName) {
      TypeName.Bool -> FallbackEncoder(typeName, CoreType.I32)
      TypeName.S8 -> FallbackEncoder(typeName, CoreType.I32)
      TypeName.S16 -> FallbackEncoder(typeName, CoreType.I32)
      TypeName.S32 -> FallbackEncoder(typeName, CoreType.I32)
      TypeName.S64 -> object : Encoder() {
        override val coreType: CoreType
          get() = CoreType.I64

        override fun coreTypeToValue(bridge: CodeBlock, coreType: CodeBlock) = coreType
        override fun valueToCoreType(bridge: CodeBlock, value: CodeBlock) = value
      }

      TypeName.U8 -> FallbackEncoder(typeName, CoreType.I32)
      TypeName.U16 -> FallbackEncoder(typeName, CoreType.I32)
      TypeName.U32 -> FallbackEncoder(typeName, CoreType.I32)
      TypeName.U64 -> FallbackEncoder(typeName, CoreType.I64)
      TypeName.F32 -> FallbackEncoder(typeName, CoreType.F32)
      TypeName.F64 -> FallbackEncoder(typeName, CoreType.F64)
      TypeName.Char -> FallbackEncoder(typeName, CoreType.I32)
      TypeName.String -> StringEncoder1

      is TypeName.Stream -> FallbackEncoder(typeName, CoreType.I32)
      is TypeName.Tuple -> FallbackEncoder(typeName, CoreType.I32) // TODO: Record.
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
      is TypeName.Result -> FallbackEncoder(typeName, CoreType.I32) // TODO: Tuple.
    }
  }
}

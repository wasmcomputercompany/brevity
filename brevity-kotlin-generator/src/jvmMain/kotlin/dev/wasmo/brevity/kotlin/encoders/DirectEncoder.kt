package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

open class DirectEncoder(
  override val type: TypeName,
  override val alignment: Int,
  val coreType: CoreType,
  val integerType: IntegerType,
) : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(coreType)

  override val byteCount: Int
    get() = integerType.byteCount

  context(codeBuilder: CodeBuilder, context: Context)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return integerTypeToValue(codeBuilder.platform.load(baseAddress, offset, integerType))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    codeBuilder.platform.store(baseAddress, offset, integerType, valueToIntegerType(value))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(coreTypeToValue(transformer.take()))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    transformer: Transformer,
  ) {
    transformer.put(valueToCoreType(transformer.take()))
  }

  open fun coreTypeToValue(coreType: CodeBlock): CodeBlock = coreType

  open fun valueToCoreType(value: CodeBlock): CodeBlock = value

  open fun integerTypeToValue(integerType: CodeBlock): CodeBlock = coreTypeToValue(integerType)

  open fun valueToIntegerType(value: CodeBlock): CodeBlock = valueToCoreType(value)
}

object BooleanEncoder : DirectEncoder(
  type = TypeName.Bool,
  alignment = 1,
  coreType = CoreType.I32,
  integerType = IntegerType.S8,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("(%L != 0)", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("(if (%L) 1 else 0)", value)

  override fun integerTypeToValue(integerType: CodeBlock): CodeBlock =
    CodeBlock.of("(%L.toInt() != 0)", integerType)


  override fun valueToIntegerType(value: CodeBlock) =
    CodeBlock.of("(if (%L) 1 else 0)", value)
}

object ByteEncoder : DirectEncoder(
  type = TypeName.S8,
  alignment = 1,
  coreType = CoreType.I32,
  integerType = IntegerType.S8,
) {
  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)

  override fun valueToIntegerType(value: CodeBlock): CodeBlock =
    CodeBlock.of("%L.toByte()", value)

  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toByte()", coreType)
}

object ShortEncoder : DirectEncoder(
  type = TypeName.S16,
  alignment = 2,
  coreType = CoreType.I32,
  integerType = IntegerType.S16,
) {
  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)

  override fun valueToIntegerType(value: CodeBlock): CodeBlock =
    CodeBlock.of("%L.toShort()", value)

  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toShort()", coreType)
}

object IntEncoder : DirectEncoder(
  type = TypeName.S32,
  alignment = 4,
  coreType = CoreType.I32,
  integerType = IntegerType.S32,
)

object LongEncoder : DirectEncoder(
  type = TypeName.S64,
  alignment = 8,
  coreType = CoreType.I64,
  integerType = IntegerType.S64,
)

object UByteEncoder : DirectEncoder(
  type = TypeName.U8,
  alignment = 1,
  coreType = CoreType.I32,
  integerType = IntegerType.S8,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUByte()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)

  override fun valueToIntegerType(value: CodeBlock) =
    CodeBlock.of("%L.toByte()", value)
}

object UShortEncoder : DirectEncoder(
  type = TypeName.U16,
  alignment = 2,
  coreType = CoreType.I32,
  integerType = IntegerType.S16,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUShort()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)

  override fun valueToIntegerType(value: CodeBlock) =
    CodeBlock.of("%L.toShort()", value)
}

object UIntEncoder : DirectEncoder(
  type = TypeName.U32,
  alignment = 4,
  coreType = CoreType.I32,
  integerType = IntegerType.S32,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUInt()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object ULongEncoder : DirectEncoder(
  type = TypeName.U64,
  alignment = 8,
  coreType = CoreType.I64,
  integerType = IntegerType.S64,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toULong()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toLong()", value)
}

object FloatEncoder : DirectEncoder(
  type = TypeName.F32,
  alignment = 4,
  coreType = CoreType.F32,
  integerType = IntegerType.S32,
) {
  override fun integerTypeToValue(integerType: CodeBlock) =
    CoreType.F32.fromBits(CoreType.I32, integerType)

  override fun valueToIntegerType(value: CodeBlock) =
    CoreType.F32.toBits(CoreType.I32, value)
}

object DoubleEncoder : DirectEncoder(
  type = TypeName.F64,
  alignment = 8,
  coreType = CoreType.F64,
  integerType = IntegerType.S64,
) {
  override fun integerTypeToValue(integerType: CodeBlock) =
    CoreType.F64.fromBits(CoreType.I64, integerType)

  override fun valueToIntegerType(value: CodeBlock) =
    CoreType.F64.toBits(CoreType.I64, value)
}

object CharEncoder : DirectEncoder(
  type = TypeName.Char,
  alignment = 4,
  coreType = CoreType.I32,
  integerType = IntegerType.S32,
)

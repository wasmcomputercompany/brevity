package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder

open class DirectEncoder(
  override val alignment: Int,
  val coreType: CoreType,
  val integerType: IntegerType,
) : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(coreType)

  override val byteCount: Int
    get() = integerType.byteCount

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return integerTypeToValue(codeBuilder.platform.load(baseAddress, offset, integerType))
  }

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    codeBuilder.platform.store(baseAddress, offset, integerType, valueToIntegerType(value))
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(coreTypeToValue(transformer.take()))
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    transformer.put(valueToCoreType(transformer.take()))
  }

  open fun coreTypeToValue(coreType: CodeBlock): CodeBlock = coreType

  open fun valueToCoreType(value: CodeBlock): CodeBlock = value

  open fun integerTypeToValue(integerType: CodeBlock): CodeBlock = coreTypeToValue(integerType)

  open fun valueToIntegerType(value: CodeBlock): CodeBlock = valueToCoreType(value)
}

object BooleanEncoder : DirectEncoder(
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
  alignment = 1,
  coreType = CoreType.I32,
  integerType = IntegerType.S8,
) {
  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)

  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toByte()", coreType)
}

object ShortEncoder : DirectEncoder(
  alignment = 2,
  coreType = CoreType.I32,
  integerType = IntegerType.S16,
) {
  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)

  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toShort()", coreType)
}

object IntEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.I32,
  integerType = IntegerType.S32,
)

object LongEncoder : DirectEncoder(
  alignment = 8,
  coreType = CoreType.I64,
  integerType = IntegerType.S64,
)

object UByteEncoder : DirectEncoder(
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
  alignment = 4,
  coreType = CoreType.I64,
  integerType = IntegerType.S64,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toULong()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toLong()", value)
}

object FloatEncoder : DirectEncoder(
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
  alignment = 4,
  coreType = CoreType.I32,
  integerType = IntegerType.S32,
)

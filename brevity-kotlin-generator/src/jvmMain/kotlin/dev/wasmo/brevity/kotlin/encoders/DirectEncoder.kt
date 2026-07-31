package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder

open class DirectEncoder(
  override val alignment: Int,
  val coreType: CoreType,
) : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(coreType)

  override val byteCount: Int
    get() = coreType.byteCount

  context(codeBuilder: CodeBuilder)
  override fun load(
      baseAddress: CodeBlock,
      offset: Int,
  ): CodeBlock {
    return coreTypeToValue(codeBuilder.platform.load(baseAddress, offset, coreType))
  }

  context(codeBuilder: CodeBuilder)
  override fun store(
      baseAddress: CodeBlock,
      offset: Int,
      value: CodeBlock,
  ) {
    codeBuilder.platform.store(baseAddress, offset, coreType, valueToCoreType(value))
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
}


object BooleanEncoder : DirectEncoder(
  alignment = 1,
  coreType = CoreType.I32,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("(%L != 0)", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("(if (%L) 1 else 0)", value)
}

object ByteEncoder : DirectEncoder(
  alignment = 1,
  coreType = CoreType.I32,
)

object ShortEncoder : DirectEncoder(
  alignment = 2,
  coreType = CoreType.I32,
)

object IntEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.I32,
)

object LongEncoder : DirectEncoder(
  alignment = 8,
  coreType = CoreType.I64,
)

object UByteEncoder : DirectEncoder(
  alignment = 1,
  coreType = CoreType.I32,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUByte()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object UShortEncoder : DirectEncoder(
  alignment = 2,
  coreType = CoreType.I32,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUShort()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object UIntEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.I32,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toUInt()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toInt()", value)
}

object ULongEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.I64,
) {
  override fun coreTypeToValue(coreType: CodeBlock) =
    CodeBlock.of("%L.toULong()", coreType)

  override fun valueToCoreType(value: CodeBlock) =
    CodeBlock.of("%L.toLong()", value)
}

object FloatEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.F32,
)

object DoubleEncoder : DirectEncoder(
  alignment = 8,
  coreType = CoreType.F64,
)

object CharEncoder : DirectEncoder(
  alignment = 4,
  coreType = CoreType.I32,
)

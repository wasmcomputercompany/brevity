package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.generator.plus

/** An encoder that invokes a function like [EncoderFactory.Load]. */
class CallDeclaredTypeEncoder(
  private val encoderFactory: EncoderFactory,
  private val type: IrTypeDeclaration,
  private val typeEncoder: Encoder,
) : Encoder() {
  override val coreTypes: List<CoreType>
    get() = typeEncoder.coreTypes

  override val nameHints: List<Identifier>?
    get() = typeEncoder.nameHints

  override val byteCount: Int
    get() = typeEncoder.byteCount

  override val alignment: Int
    get() = typeEncoder.alignment

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = encoderFactory.load(type).call(baseAddress + offset)

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    encoderFactory.store(type).call(baseAddress + offset, value)
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    encoderFactory.liftFlat(type).call(transformer)
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    encoderFactory.lowerFlat(type).call(transformer)
  }
}

package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.ir.IrTypeDeclaration
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.generator.DeclaredTypeLiftFlatGenerator
import dev.wasmo.brevity.kotlin.generator.DeclaredTypeLoadGenerator
import dev.wasmo.brevity.kotlin.generator.DeclaredTypeLowerFlatGenerator
import dev.wasmo.brevity.kotlin.generator.DeclaredTypeStoreGenerator
import dev.wasmo.brevity.kotlin.generator.plus

/** An encoder that invokes a function like [DeclaredTypeLoadGenerator]. */
class CallDeclaredTypeEncoder(
  val type: IrTypeDeclaration,
  val typeEncoder: Encoder,
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
  ): CodeBlock {
    val subject = DeclaredTypeLoadGenerator(
      type = type,
      encoder = typeEncoder,
      platform = codeBuilder.platform,
    )
    return subject.call(baseAddress + offset)
  }

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val subject = DeclaredTypeStoreGenerator(
      type = type,
      encoder = typeEncoder,
      platform = codeBuilder.platform,
    )
    return subject.call(baseAddress + offset, value)
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    val subject = DeclaredTypeLiftFlatGenerator(
      type = type,
      encoder = typeEncoder,
      platform = codeBuilder.platform,
    )
    return subject.call(transformer)
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    val subject = DeclaredTypeLowerFlatGenerator(
      type = type,
      encoder = typeEncoder,
      platform = codeBuilder.platform,
    )
    return subject.call(transformer)
  }
}

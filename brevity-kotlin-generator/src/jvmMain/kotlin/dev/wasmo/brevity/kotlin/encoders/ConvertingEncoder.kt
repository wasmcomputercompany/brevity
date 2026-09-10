package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.kotlin.code.CodeBuilder

/**
 * Wraps another encoder, transforming the value in and out.
 */
abstract class ConvertingEncoder(
  val delegate: Encoder,
) : Encoder() {
  override val coreTypes: List<CoreType>
    get() = delegate.coreTypes
  override val byteCount: Int
    get() = delegate.byteCount
  override val alignment: Int
    get() = delegate.alignment
  override val nameHints: List<Identifier>?
    get() = delegate.nameHints

  abstract fun encode(codeBlock: CodeBlock): CodeBlock
  abstract fun decode(codeBlock: CodeBlock): CodeBlock

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = decode(delegate.load(baseAddress, offset))

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) = delegate.store(baseAddress, offset, encode(value))

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    val coreValues = buildList {
      for (i in delegate.coreTypes.indices) {
        add(transformer.take())
      }
    }
    transformer.put(decode(delegate.liftFlat(coreValues)))
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    val coreValues = delegate.lowerFlat(encode(transformer.take()))
    for (i in delegate.coreTypes.indices) {
      transformer.put(coreValues[i])
    }
  }
}

package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

/**
 * Wraps another encoder, transforming the value in and out.
 */
abstract class ConvertingEncoder(
  val delegate: Encoder,
) : Encoder() {
  override val type: TypeName
    get() = delegate.type
  override val coreTypes: List<CoreType>
    get() = delegate.coreTypes
  override val byteCount: Int
    get() = delegate.byteCount
  override val alignment: Int
    get() = delegate.alignment
  override val nameHints: List<Identifier>?
    get() = delegate.nameHints
  override val lowerAllocates: Boolean
    get() = delegate.lowerAllocates
  override val lowerFlatRequiresCallBuilder: Boolean
    get() = delegate.lowerFlatRequiresCallBuilder

  abstract fun encode(codeBlock: CodeBlock): CodeBlock
  abstract fun decode(codeBlock: CodeBlock): CodeBlock

  context(codeBuilder: CodeBuilder, context: Context)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = decode(context.load(delegate, baseAddress, offset))

  context(codeBuilder: CodeBuilder, context: Context)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) = context.store(
    encoder = delegate,
    memoryAllocator = memoryAllocator,
    baseAddress = baseAddress,
    offset = offset,
    value = encode(value),
  )

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(liftFlatIo: LiftFlatIo) {
    val coreValues = buildList {
      for (i in delegate.coreTypes.indices) {
        add(liftFlatIo.take())
      }
    }
    liftFlatIo.put(decode(context.liftFlat(delegate, coreValues)))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  ) = context.lowerFlat(delegate, memoryAllocator, lowerFlatIo, encode(value))
}

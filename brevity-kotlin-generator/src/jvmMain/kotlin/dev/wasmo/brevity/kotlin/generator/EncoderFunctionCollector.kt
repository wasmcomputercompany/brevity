package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.encoders.Encoder

class EncoderFunctionCollector(
  private val qualifiedSpecCollector: QualifiedSpecCollector,
) {
  fun encoderContext(): Encoder.Context = object : Encoder.Context {
    context(codeBuilder: CodeBuilder)
    override fun lowerFlat(
      encoder: Encoder,
      value: CodeBlock,
      memoryAllocator: MemoryAllocator?,
    ): List<CodeBlock> = context(this) {
      encoder.lowerFlat(memoryAllocator, value)
    }

    context(codeBuilder: CodeBuilder)
    override fun liftFlat(
      encoder: Encoder,
      values: List<CodeBlock>,
    ): CodeBlock = context(this) {
      encoder.liftFlat(values)
    }

    context(codeBuilder: CodeBuilder)
    override fun load(
      encoder: Encoder,
      baseAddress: CodeBlock,
      offset: Int,
    ): CodeBlock = context(this) {
      encoder.load(baseAddress, offset)
    }

    context(codeBuilder: CodeBuilder)
    override fun store(
      encoder: Encoder,
      memoryAllocator: MemoryAllocator?,
      baseAddress: CodeBlock,
      offset: Int,
      value: CodeBlock,
    ): Unit = context(this) {
      encoder.store(memoryAllocator, baseAddress, offset, value)
    }
  }
}

package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.Encoder.Context

class StreamBindingsCollector(
  val typeNamer: TypeNamer,
) {
  /** Lower an API value like a resource instance to an ABI value like a memory address. */
  context(codeBuilder: CodeBuilder, context: Context)
  fun initBindingsAndLowerFlat(
    encoder: Encoder,
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  ) {
    val receiveStream = initBinding(encoder, value)
    encoder.lowerFlat(memoryAllocator, lowerFlatIo, receiveStream)
  }

  context(codeBuilder: CodeBuilder, context: Context)
  fun liftFlatAndInitBindings(
    encoder: Encoder,
    values: List<CodeBlock>,
  ): CodeBlock {
    val value = encoder.liftFlat(values)
    return initBinding(encoder, value)
  }

  context(codeBuilder: CodeBuilder, context: Context)
  fun loadAndInitBindings(
    encoder: Encoder,
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    val value = encoder.load(baseAddress, offset)
    return initBinding(encoder, value)
  }

  context(codeBuilder: CodeBuilder, context: Context)
  fun initBindingsAndStore(
    encoder: Encoder,
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val receiveStream = initBinding(encoder, value)
    encoder.store(memoryAllocator, baseAddress, offset, receiveStream)
  }

  context(codeBuilder: CodeBuilder)
  private fun initBinding(
    encoder: Encoder,
    value: CodeBlock,
  ): CodeBlock {
    val receiveStreamName = codeBuilder.newName("receiveStream")
    codeBuilder.addStatement("val %N = %L", receiveStreamName, value)

    codeBuilder.add("%N.initBinding(", receiveStreamName)
    if (codeBuilder.platform is HostPlatform) {
      codeBuilder.add("%L, ", codeBuilder.bridge)
    }
    codeBuilder.add("%L)\n", typeNamer.streamBindingInstance(encoder.type))

    return CodeBlock.of("%N", receiveStreamName)
  }
}

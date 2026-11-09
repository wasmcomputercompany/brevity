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
    val valueName = codeBuilder.newName("value")
    codeBuilder.addStatement("val %N = %L", valueName, value)

    codeBuilder.add("%N.initBinding(", valueName)
    if (codeBuilder.platform is HostPlatform) {
      codeBuilder.add("%L, ", codeBuilder.bridge)
    }
    codeBuilder.add("%L)\n", typeNamer.streamBindingInstance(encoder.type))

    encoder.lowerFlat(memoryAllocator, lowerFlatIo, CodeBlock.of("%N", valueName))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  fun liftFlatAndInitBindings(
    encoder: Encoder,
    values: List<CodeBlock>,
  ): CodeBlock {
    return encoder.liftFlat(values)
  }

  context(codeBuilder: CodeBuilder, context: Context)
  fun loadAndInitBindings(
    encoder: Encoder,
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return encoder.load(baseAddress, offset)
  }

  context(codeBuilder: CodeBuilder, context: Context)
  fun initBindingsAndStore(
    encoder: Encoder,
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    encoder.store(memoryAllocator, baseAddress, offset, value)
  }
}

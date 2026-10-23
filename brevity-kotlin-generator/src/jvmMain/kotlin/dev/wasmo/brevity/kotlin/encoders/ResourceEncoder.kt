package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

internal class ResourceEncoder(
  private val abiClassName: ClassName,
) : Encoder() {
  override val coreTypes = listOf(CoreType.I32)

  override val byteCount: Int
    get() = CoreType.I32.byteCount

  override val alignment: Int
    get() = CoreType.I32.alignment

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return liftResource(codeBuilder.platform.load(baseAddress, offset, CoreType.Pointer))
  }

  context(codeBuilder: CodeBuilder)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    codeBuilder.platform.store(baseAddress, offset, CoreType.Pointer, lowerResource(value))
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(liftResource(transformer.take()))
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    transformer: Transformer,
  ) {
    transformer.put(lowerResource(transformer.take()))
  }

  context(codeBuilder: CodeBuilder)
  fun liftResource(id: CodeBlock): CodeBlock = liftResource(id, abiClassName)

  context(codeBuilder: CodeBuilder)
  fun lowerResource(resource: CodeBlock) = lowerResource(resource, abiClassName)
}

context(codeBuilder: CodeBuilder)
fun liftResource(id: CodeBlock, abiClassName: ClassName) = CodeBlock.of(
  "%L.fromIndex<%T>(%L)",
  codeBuilder.bridge,
  abiClassName,
  id,
)

context(codeBuilder: CodeBuilder)
fun lowerResource(resource: CodeBlock, abiClassName: ClassName) = CodeBlock.of(
  "%L.toIndex<%T>(%L)",
  codeBuilder.bridge,
  abiClassName,
  resource,
)

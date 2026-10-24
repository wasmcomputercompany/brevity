package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

internal class ResourceEncoder(
  override val brevityTypeName: TypeName.Declared,
  private val abiClassName: ClassName,
) : Encoder() {
  override val coreTypes = listOf(CoreType.I32)

  override val byteCount: Int
    get() = CoreType.I32.byteCount

  override val alignment: Int
    get() = CoreType.I32.alignment

  context(codeBuilder: CodeBuilder, context: Context)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return liftResource(
      index = codeBuilder.platform.load(
        baseAddress,
        offset,
        CoreType.Pointer,
      ),
      orientation = context.getOrientation(brevityTypeName),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    codeBuilder.platform.store(
      baseAddress,
      offset,
      CoreType.Pointer,
      lowerResource(
        resource = value,
        orientation = context.getOrientation(brevityTypeName),
      ),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(
      liftResource(
        index = transformer.take(),
        orientation = context.getOrientation(brevityTypeName),
      ),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    transformer: Transformer,
  ) {
    transformer.put(
      lowerResource(
        resource = transformer.take(),
        orientation = context.getOrientation(brevityTypeName),
      ),
    )
  }

  context(codeBuilder: CodeBuilder)
  fun liftResource(index: CodeBlock, orientation: Orientation): CodeBlock =
    codeBuilder.platform.liftResource(index, abiClassName, orientation)

  context(codeBuilder: CodeBuilder)
  fun lowerResource(resource: CodeBlock, orientation: Orientation) =
    codeBuilder.platform.lowerResource(resource, brevityTypeName, orientation)
}

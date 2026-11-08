package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

abstract class HandleEncoder(
  override val type: TypeName,
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
    return liftHandle(
      index = codeBuilder.platform.load(
        baseAddress,
        offset,
        CoreType.Pointer,
      ),
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
      lowerHandle(
        handle = value,
      ),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(liftFlatIo: LiftFlatIo) {
    liftFlatIo.put(
      liftHandle(
        index = liftFlatIo.take(),
      ),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  ) {
    lowerFlatIo.put(lowerHandle(handle = value))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  abstract fun liftHandle(index: CodeBlock): CodeBlock

  context(codeBuilder: CodeBuilder, context: Context)
  abstract fun lowerHandle(handle: CodeBlock): CodeBlock
}

package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

/** Stores a string as an address pointer and a byte count. */
object StringEncoder : Encoder() {
  override val type: TypeName
    get() = TypeName.String

  override val coreTypes: List<CoreType>
    get() = listOf(CoreType.Pointer, CoreType.I32)

  override val nameHints: List<Identifier>
    get() = listOf(Identifier("pointer"), Identifier("byte-count"))

  override val byteCount: Int
    get() = 8

  override val alignment: Int
    get() = CoreType.Pointer.alignment

  override val lowerAllocates: Boolean
    get() = true

  override val lowerFlatRequiresCallBuilder: Boolean
    get() = false

  context(codeBuilder: CodeBuilder, context: Context)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    val address = codeBuilder.newName("stringAddress")
    val byteCount = codeBuilder.newName("stringByteCount")
    codeBuilder.addStatement(
      "val %N = %L",
      address,
      codeBuilder.platform.load(baseAddress, offset, CoreType.Pointer),
    )
    codeBuilder.addStatement(
      "val %N = %L",
      byteCount,
      codeBuilder.platform.load(baseAddress, offset + 4, CoreType.I32),
    )
    return codeBuilder.platform.loadString(
      CodeBlock.of("%N", address),
      CodeBlock.of("%N", byteCount),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val (addressCodeBlock, byteCountCodeBlock) = codeBuilder.platform.storeString(
      memoryAllocator!!,
      value,
    )
    val address = codeBuilder.newName("stringAddress")
    val byteCount = codeBuilder.newName("stringByteCount")
    codeBuilder.addStatement(
      "val %N = %L",
      address,
      addressCodeBlock,
    )
    codeBuilder.addStatement(
      "val %N = %L",
      byteCount,
      byteCountCodeBlock,
    )
    codeBuilder.platform.store(baseAddress, offset, CoreType.Pointer, CodeBlock.of("%N", address))
    codeBuilder.platform.store(baseAddress, offset + 4, CoreType.I32, CodeBlock.of("%N", byteCount))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(liftFlatIo: LiftFlatIo) {
    liftFlatIo.put(codeBuilder.platform.loadString(liftFlatIo.take(), liftFlatIo.take()))
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  ) {
    val (address, size) = codeBuilder.platform.storeString(memoryAllocator!!, value)
    lowerFlatIo.put(address)
    lowerFlatIo.put(size)
  }
}

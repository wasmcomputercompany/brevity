package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.kotlin.code.CodeBuilder

/** Stores a string as an address pointer and a byte count. */
object StringEncoder : Encoder() {
  override val coreTypes: List<CoreType>
    get() = listOf(CoreType.Pointer, CoreType.I32)

  override val nameHints: List<Identifier>
    get() = listOf(Identifier("pointer"), Identifier("byte-count"))

  override val byteCount: Int
    get() = 8

  override val alignment: Int
    get() = CoreType.Pointer.alignment

  context(codeBuilder: CodeBuilder)
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

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val (addressCodeBlock, byteCountCodeBlock) = codeBuilder.platform.storeString(value)
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

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(codeBuilder.platform.loadString(transformer.take(), transformer.take()))
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    val (address, size) = codeBuilder.platform.storeString(transformer.take())
    transformer.put(address)
    transformer.put(size)
  }
}

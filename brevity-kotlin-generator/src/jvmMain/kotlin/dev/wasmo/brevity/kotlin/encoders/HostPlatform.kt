package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.generator.Symbols

internal object HostPlatform : Platform {
  context(builder: EncodeBuilder)
  override fun allocate(
    memoryAllocatorName: String,
    byteCount: CodeBlock,
  ) = CodeBlock.of("%L.allocate(%L)", builder.bridge, byteCount)

  context(builder: EncodeBuilder)
  override fun lowerAddress(address: CodeBlock) = address

  context(builder: EncodeBuilder)
  override fun liftAddress(address: CodeBlock) = address

  context(builder: EncodeBuilder)
  override fun loadString(address: CodeBlock, byteCount: CodeBlock): CodeBlock {
    return CodeBlock.of(
      "%L.memory.readString(%L, %L)",
      builder.bridge,
      address,
      byteCount,
    )
  }

  context(builder: EncodeBuilder)
  override fun storeString(string: CodeBlock): Pair<CodeBlock, CodeBlock> {
    val byteArray = builder.nameAllocator.newName("byteArray")
    val pointer = builder.nameAllocator.newName("pointer")

    builder.code.addStatement(
      "val %N = %L.%M()",
      byteArray,
      string,
      Symbols.Kotlin.EncodeToByteArray,
    )
    builder.code.addStatement(
      "val %N = %L",
      pointer,
      builder.allocate("%N.size", byteArray),
    )
    builder.code.addStatement(
      "%L.memory.write(%N, %N)",
      builder.bridge,
      pointer,
      byteArray,
    )

    return CodeBlock.of("%N", pointer) to CodeBlock.of("%N.size", byteArray)
  }

  context(builder: EncodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
    type: CoreType,
  ): CodeBlock {
    return CodeBlock.of(
      "%L.memory.%N(%L)",
      builder.bridge,
      when (type) {
        CoreType.I64 -> "readLong"
        else -> "readInt"
      },
      when {
        offset != 0 -> CodeBlock.of("%L + %L", baseAddress, offset)
        else -> baseAddress
      },
    )
  }

  context(builder: EncodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    type: CoreType,
    value: CodeBlock,
  ) {
    builder.code.addStatement(
      "%L.memory.%N(%L, %L)",
      builder.bridge,
      when (type) {
        CoreType.I64 -> "writeLong"
        else -> "writeI32"
      },
      when {
        offset != 0 -> CodeBlock.of("%L + %L", baseAddress, offset)
        else -> baseAddress
      },
      value,
    )
  }
}

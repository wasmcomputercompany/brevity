package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.kotlinApi

internal object HostPlatform : Platform {
  context(builder: BridgeBuilder)
  override fun allocate(
    memoryAllocatorName: String,
    byteCount: CodeBlock,
  ) = CodeBlock.of("%L.allocate(%L)", builder.bridge, byteCount)

  override fun lowerAddress(address: CodeBlock) = address

  override fun liftAddress(address: CodeBlock) = address

  context(builder: BridgeBuilder)
  override fun liftResource(id: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of(
      "%L.%M<%T>(%L)",
      builder.bridge,
      Symbols.Brevity.HostBridgeGet,
      handleType.kotlinApi,
      id,
    )

  context(builder: BridgeBuilder)
  override fun lowerResource(resource: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of("%L.toId<%T>(%L)", builder.bridge, handleType.kotlinApi, resource)

  context(builder: BridgeBuilder)
  override fun loadString(address: CodeBlock, byteCount: CodeBlock) =
    CodeBlock.of("%L.memory.readString(%L, %L)", builder.bridge, address, byteCount)

  context(builder: BridgeBuilder)
  override fun storeString(string: CodeBlock): Pair<CodeBlock, CodeBlock> {
    val byteArray = builder.nameAllocator.newName("byteArray")
    val stringAddress = builder.nameAllocator.newName("stringAddress")

    builder.code.addStatement(
      "val %N = %L.%M()",
      byteArray,
      string,
      Symbols.Kotlin.EncodeToByteArray,
    )
    builder.code.addStatement(
      "val %N = %L",
      stringAddress,
      builder.allocate("%N.size", byteArray),
    )
    builder.code.addStatement(
      "%L.memory.write(%N, %N)",
      builder.bridge,
      stringAddress,
      byteArray,
    )

    return CodeBlock.of("%N", stringAddress) to CodeBlock.of("%N.size", byteArray)
  }

  context(builder: BridgeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
    type: IntegerType,
  ): CodeBlock {
    return CodeBlock.of(
      "%L.memory.%N(%L)",
      builder.bridge,
      when (type) {
        IntegerType.S8 -> "read"
        IntegerType.S16 -> "readShort"
        IntegerType.S32 -> "readInt"
        IntegerType.S64 -> "readLong"
      },
      when {
        offset != 0 -> CodeBlock.of("%L + %L", baseAddress, offset)
        else -> baseAddress
      },
    )
  }

  context(builder: BridgeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    type: IntegerType,
    value: CodeBlock,
  ) {
    builder.code.addStatement(
      "%L.memory.%N(%L, %L)",
      builder.bridge,
      when (type) {
        IntegerType.S8 -> "writeByte"
        IntegerType.S16 -> "writeShort"
        IntegerType.S32 -> "writeI32"
        IntegerType.S64 -> "writeLong"
      },
      when {
        offset != 0 -> CodeBlock.of("%L + %L", baseAddress, offset)
        else -> baseAddress
      },
      value,
    )
  }
}

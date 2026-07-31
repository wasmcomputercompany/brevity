package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.encoders.IntegerType
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.kotlinApi

internal object HostPlatform : Platform {
  override val identifier: Identifier
    get() = Identifier("host")

  override val addressType: KtTypeName
    get() = INT

  override val bridgeType: KtTypeName
    get() = Symbols.Brevity.HostBridge

  context(codeBuilder: CodeBuilder)
  override fun allocate(
    memoryAllocatorName: String,
    byteCount: CodeBlock,
  ) = CodeBlock.of("%L.allocate(%L)", codeBuilder.bridge, byteCount)

  override fun lowerAddress(address: CodeBlock) = address

  override fun liftAddress(address: CodeBlock) = address

  context(codeBuilder: CodeBuilder)
  override fun liftResource(id: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of(
      "%L.%M<%T>(%L)",
      codeBuilder.bridge,
      Symbols.Brevity.HostBridgeGet,
      handleType.kotlinApi,
      id,
    )

  context(codeBuilder: CodeBuilder)
  override fun lowerResource(resource: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of("%L.toId<%T>(%L)", codeBuilder.bridge, handleType.kotlinApi, resource)

  context(codeBuilder: CodeBuilder)
  override fun loadString(address: CodeBlock, byteCount: CodeBlock) =
    CodeBlock.of("%L.memory.readString(%L, %L)", codeBuilder.bridge, address, byteCount)

  context(codeBuilder: CodeBuilder)
  override fun storeString(string: CodeBlock): Pair<CodeBlock, CodeBlock> {
    val byteArray = codeBuilder.newName("byteArray")
    val stringAddress = codeBuilder.newName("stringAddress")

    codeBuilder.addStatement(
      "val %N = %L.%M()",
      byteArray,
      string,
      Symbols.Kotlin.EncodeToByteArray,
    )
    codeBuilder.addStatement(
      "val %N = %L",
      stringAddress,
      codeBuilder.allocate("%N.size", byteArray),
    )
    codeBuilder.addStatement(
      "%L.memory.write(%N, %N)",
      codeBuilder.bridge,
      stringAddress,
      byteArray,
    )

    return CodeBlock.of("%N", stringAddress) to CodeBlock.of("%N.size", byteArray)
  }

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
    type: IntegerType,
  ): CodeBlock {
    return CodeBlock.of(
      "%L.memory.%N(%L)",
      codeBuilder.bridge,
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

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    type: IntegerType,
    value: CodeBlock,
  ) {
    codeBuilder.addStatement(
      "%L.memory.%N(%L, %L)",
      codeBuilder.bridge,
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

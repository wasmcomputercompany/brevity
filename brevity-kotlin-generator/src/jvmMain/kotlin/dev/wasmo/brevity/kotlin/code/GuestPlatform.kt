package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.encoders.IntegerType
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.handleName
import dev.wasmo.brevity.kotlin.generator.kotlinApi

object GuestPlatform : Platform {
  override val identifier: Identifier
    get() = Identifier("guest")

  override val addressType: KtTypeName
    get() = Symbols.KotlinWasm.Pointer

  override val bridgeType: KtTypeName
    get() = Symbols.Brevity.GuestBridge

  context(codeBuilder: CodeBuilder)
  override fun allocate(
    memoryAllocatorName: String,
    byteCount: CodeBlock,
  ) = CodeBlock.of("%N.allocate(%L)", memoryAllocatorName, byteCount)

  override fun liftAddress(address: CodeBlock) =
    CodeBlock.of("%T(%L.toUInt())", Symbols.KotlinWasm.Pointer, address)

  override fun lowerAddress(address: CodeBlock) =
    CodeBlock.of("%L.address.toInt()", address)

  context(codeBuilder: CodeBuilder)
  override fun liftResource(id: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of("%L.fromId(%L, ::%T)", codeBuilder.bridge, id, handleType.handleName)

  context(codeBuilder: CodeBuilder)
  override fun lowerResource(resource: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of("%L.toId<%T>(%L)", codeBuilder.bridge, handleType.kotlinApi, resource)

  context(codeBuilder: CodeBuilder)
  override fun loadString(address: CodeBlock, byteCount: CodeBlock): CodeBlock {
    return CodeBlock.of(
      "%T(%L.toUInt()).%M(%L)",
      Symbols.KotlinWasm.Pointer,
      address,
      Symbols.Brevity.LoadString,
      byteCount,
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun storeString(string: CodeBlock): Pair<CodeBlock, CodeBlock> {
    val byteArray = codeBuilder.newName("byteArray")
    val address = codeBuilder.newName("stringAddress")

    codeBuilder.addStatement(
      "val %N = %L.%M()",
      byteArray,
      string,
      Symbols.Kotlin.EncodeToByteArray,
    )
    codeBuilder.addStatement(
      "val %N = %L",
      address,
      codeBuilder.allocate("%N.size", byteArray),
    )
    codeBuilder.addStatement(
      "%N.%M(%N)",
      address,
      Symbols.Brevity.StoreByteArray,
      byteArray,
    )

    return lowerAddress(CodeBlock.of("%N", address)) to CodeBlock.of("%N.size", byteArray)
  }

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
    type: IntegerType,
  ): CodeBlock {
    return CodeBlock.of(
      "%L.%N()",
      when {
        offset != 0 -> CodeBlock.of("(%L + %L)", baseAddress, offset)
        else -> baseAddress
      },
      when (type) {
        IntegerType.S8 -> "loadByte"
        IntegerType.S16 -> "loadShort"
        IntegerType.S32 -> "loadInt"
        IntegerType.S64 -> "loadLong"
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
      "%L.%N(%L)",
      when {
        offset != 0 -> CodeBlock.of("(%L + %L)", baseAddress, offset)
        else -> baseAddress
      },
      when (type) {
        IntegerType.S8 -> "storeByte"
        IntegerType.S16 -> "storeShort"
        IntegerType.S32 -> "storeInt"
        IntegerType.S64 -> "storeLong"
      },
      value,
    )
  }
}

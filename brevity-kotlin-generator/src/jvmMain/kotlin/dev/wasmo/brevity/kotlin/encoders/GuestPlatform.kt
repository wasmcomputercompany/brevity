package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.handleName
import dev.wasmo.brevity.kotlin.generator.kotlinApi

internal object GuestPlatform : Platform {
  context(builder: EncodeBuilder)
  override fun allocate(
    memoryAllocatorName: String,
    byteCount: CodeBlock,
  ) = CodeBlock.of("%N.allocate(%L)", memoryAllocatorName, byteCount)

  context(builder: EncodeBuilder)
  override fun liftAddress(address: CodeBlock) =
    CodeBlock.of("%T(%L.toUInt())", Symbols.KotlinWasm.Pointer, address)

  context(builder: EncodeBuilder)
  override fun lowerAddress(address: CodeBlock) =
    CodeBlock.of("%L.address.toInt()", address)

  context(builder: EncodeBuilder)
  override fun liftResource(id: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of("%L.fromId(%L, ::%T)", builder.bridge, id, handleType.handleName)

  context(builder: EncodeBuilder)
  override fun lowerResource(resource: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of("%L.toId<%T>(%L)", builder.bridge, handleType.kotlinApi, resource)

  context(builder: EncodeBuilder)
  override fun loadString(address: CodeBlock, byteCount: CodeBlock): CodeBlock {
    return CodeBlock.of(
      "%T(%L.toUInt()).%M(%L)",
      Symbols.KotlinWasm.Pointer,
      address,
      Symbols.Brevity.LoadString,
      byteCount,
    )
  }

  context(builder: EncodeBuilder)
  override fun storeString(string: CodeBlock): Pair<CodeBlock, CodeBlock> {
    val byteArray = builder.nameAllocator.newName("byteArray")
    val address = builder.nameAllocator.newName("address")

    builder.code.addStatement(
      "val %N = %L.%M()",
      byteArray,
      string,
      Symbols.Kotlin.EncodeToByteArray,
    )
    builder.code.addStatement(
      "val %N = %L",
      address,
      builder.allocate("%N.size", byteArray),
    )
    builder.code.addStatement(
      "%N.%M(%N)",
      address,
      Symbols.Brevity.StoreByteArray,
      byteArray,
    )

    return CodeBlock.of("%N.address.toInt()", address) to CodeBlock.of("%N.size", byteArray)
  }

  context(builder: EncodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
    type: CoreType,
  ): CodeBlock {
    return CodeBlock.of(
      "%L.%N()",
      when {
        offset != 0 -> CodeBlock.of("(%L + %L)", baseAddress, offset)
        else -> baseAddress
      },
      when (type) {
        CoreType.I64 -> "loadLong"
        else -> "loadInt"
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
      "%L.%N(%L)",
      when {
        offset != 0 -> CodeBlock.of("(%L + %L)", baseAddress, offset)
        else -> baseAddress
      },
      when (type) {
        CoreType.I64 -> "storeLong"
        else -> "storeInt"
      },
      value,
    )
  }
}

package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.encoders.IntegerType
import dev.wasmo.brevity.kotlin.generator.BridgeFunction
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.importFunctionName
import dev.wasmo.brevity.kotlin.generator.plus

class GuestPlatform(
  private val kotlinMapper: KotlinMapper,
) : Platform {
  override val identifier: Identifier
    get() = Identifier("guest")

  override val addressType: KtTypeName
    get() = Symbols.KotlinWasm.Pointer

  override val bridgeType: KtTypeName
    get() = Symbols.Brevity.GuestBridge

  context(codeBuilder: CodeBuilder)
  override fun beginMemoryAllocationScope(): MemoryAllocator {
    val name = codeBuilder.newName("memoryAllocator")
    codeBuilder.beginControlFlow(
      "%M { %N ->",
      Symbols.KotlinWasm.WithScopedMemoryAllocator,
      name,
    )
    return GuestMemoryAllocator(name)
  }

  context(codeBuilder: CodeBuilder)
  override fun endMemoryAllocationScope() {
    codeBuilder.endControlFlow()
  }

  override fun getMemoryAllocator(name: String): MemoryAllocator = GuestMemoryAllocator(name)

  override fun liftAddress(address: CodeBlock) =
    CodeBlock.of("%T(%L.toUInt())", Symbols.KotlinWasm.Pointer, address)

  override fun lowerAddress(address: CodeBlock) =
    CodeBlock.of("%L.address.toInt()", address)

  context(codeBuilder: CodeBuilder)
  override fun liftResource(id: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of(
      "%L.fromId(%L, ::%T)",
      codeBuilder.bridge,
      id,
      kotlinMapper.getHandleName(handleType),
    )

  context(codeBuilder: CodeBuilder)
  override fun lowerResource(resource: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of(
      "%L.toId<%T>(%L)",
      codeBuilder.bridge,
      kotlinMapper.getAbiClassName(handleType),
      resource,
    )

  context(codeBuilder: CodeBuilder)
  override fun afterLiftParameters() {
    codeBuilder.addStatement(
      "%M()",
      Symbols.KotlinWasm.FreeAllComponentModelReallocAllocatedMemory,
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun afterLiftResult() {
    codeBuilder.addStatement(
      "%M()",
      Symbols.KotlinWasm.FreeAllComponentModelReallocAllocatedMemory,
    )
  }

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
  override fun storeString(
    memoryAllocator: MemoryAllocator,
    string: CodeBlock,
  ): Pair<CodeBlock, CodeBlock> {
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
      memoryAllocator.allocate("%N.size", byteArray),
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
      "(%L).%N()",
      baseAddress + offset,
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
      "(%L).%N(%L)",
      baseAddress + offset,
      when (type) {
        IntegerType.S8 -> "storeByte"
        IntegerType.S16 -> "storeShort"
        IntegerType.S32 -> "storeInt"
        IntegerType.S64 -> "storeLong"
      },
      value,
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun invokeLowered(
    name: FunctionName,
    parameterValues: List<CodeBlock>,
    result: BridgeFunction.Result?,
  ) {
    if (result != null) {
      codeBuilder.add("val %N = ", result.loweredName)
    }
    codeBuilder.add("%N(⇥", name.importFunctionName)
    if (parameterValues.isNotEmpty()) {
      codeBuilder.add("\n")
    }
    for (parameterValue in parameterValues) {
      codeBuilder.add("%L,\n", parameterValue)
    }
    codeBuilder.add("⇤)\n")
  }

  private class GuestMemoryAllocator(
    override val name: String,
  ) : MemoryAllocator {
    override val type: KtTypeName
      get() = Symbols.KotlinWasm.MemoryAllocator

    override fun allocate(byteCount: CodeBlock) =
      CodeBlock.of("%N.allocate(%L)", name, byteCount)
  }
}

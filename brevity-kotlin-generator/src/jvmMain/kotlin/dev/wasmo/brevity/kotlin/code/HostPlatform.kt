package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.IntegerType
import dev.wasmo.brevity.kotlin.expressions.AbiFunction
import dev.wasmo.brevity.kotlin.expressions.AbiInterface
import dev.wasmo.brevity.kotlin.expressions.AbiWorld
import dev.wasmo.brevity.kotlin.expressions.ChicoryExportFunction
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.plus

class HostPlatform(
  private val kotlinMapper: KotlinMapper,
) : Platform {
  override val identifier: Identifier
    get() = Identifier("host")

  override val addressType: KtTypeName
    get() = INT

  override val bridgeType: KtTypeName
    get() = Symbols.Brevity.HostBridge

  context(codeBuilder: CodeBuilder)
  override fun beginMemoryAllocationScope(): MemoryAllocator {
    val name = codeBuilder.newName("memoryAllocator")
    codeBuilder.beginControlFlow(
      "%L.memoryAllocator.let { %N ->",
      codeBuilder.bridge,
      name,
    )
    return HostMemoryAllocator(name)
  }

  context(codeBuilder: CodeBuilder)
  override fun endMemoryAllocationScope() {
    codeBuilder.endControlFlow()
  }

  override fun getMemoryAllocator(name: String): MemoryAllocator = HostMemoryAllocator(name)

  override fun lowerAddress(address: CodeBlock) = address

  override fun liftAddress(address: CodeBlock) = address

  context(codeBuilder: CodeBuilder)
  override fun liftResource(id: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of(
      "%L.%M<%T>(%L)",
      codeBuilder.bridge,
      Symbols.Brevity.HostBridgeGet,
      kotlinMapper.getAbiClassName(handleType),
      id,
    )

  context(codeBuilder: CodeBuilder)
  override fun lowerResource(resource: CodeBlock, handleType: TypeName.Declared) =
    CodeBlock.of(
      "%L.toId<%T>(%L)",
      codeBuilder.bridge,
      kotlinMapper.getAbiClassName(handleType),
      resource,
    )

  override fun getParent(parent: AbiWorld): KtExpression {
    return CodeBlockExpression(
      type = parent.apiInterfaceName(Orientation.Import),
      nameHint = "host",
      code = CodeBlock.of("%N", "host"),
      immediate = true,
    )
  }

  override fun getParent(parent: AbiInterface): KtExpression {
    return CodeBlockExpression(
      type = parent.apiClassName,
      nameHint = "host",
      code = CodeBlock.of("%N.%N", "host", parent.instanceName),
      immediate = true,
    )
  }

  /** Everything in Chicory is a [Long], so we need to convert core types. */
  override fun runtimeValueToCoreValue(
    value: CodeBlock,
    coreType: CoreType,
  ) = when (coreType) {
    CoreType.F32 -> CodeBlock.of("%T.fromBits(%L.toInt())", FLOAT, value)
    CoreType.F64 -> CodeBlock.of("%T.fromBits(%L)", DOUBLE, value)
    CoreType.I32 -> CodeBlock.of("%L.toInt()", value)
    CoreType.I64 -> value
    CoreType.Pointer -> CodeBlock.of("%L.toInt()", value)
  }

  /** Everything in Chicory is a [Long], so we need to convert core types. */
  override fun coreValueToRuntimeValue(
    value: CodeBlock,
    coreType: CoreType,
  ) = when (coreType) {
    CoreType.F32 -> CodeBlock.of("%L.toBits().toLong()", value)
    CoreType.F64 -> CodeBlock.of("%L.toBits()", value)
    CoreType.I32 -> CodeBlock.of("%L.toLong()", value)
    CoreType.I64 -> value
    CoreType.Pointer -> CodeBlock.of("%L.toLong()", value)
  }

  context(codeBuilder: CodeBuilder)
  override fun loadString(address: CodeBlock, byteCount: CodeBlock) =
    CodeBlock.of("%L.memory.readString(%L, %L)", codeBuilder.bridge, address, byteCount)

  context(codeBuilder: CodeBuilder)
  override fun storeString(
    memoryAllocator: MemoryAllocator,
    string: CodeBlock,
  ): Pair<CodeBlock, CodeBlock> {
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
      memoryAllocator.allocate("%N.size", byteArray),
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
      baseAddress + offset,
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
      baseAddress + offset,
      value,
    )
  }

  override fun createLowered(abiFunction: AbiFunction): KtFunction =
    ChicoryExportFunction(abiFunction)

  private class HostMemoryAllocator(
    override val name: String,
  ) : MemoryAllocator {
    override val type: KtTypeName
      get() = Symbols.Brevity.MemoryAllocator

    override fun allocate(byteCount: CodeBlock) =
      CodeBlock.of("%N.allocate(%L)", name, byteCount)
  }
}

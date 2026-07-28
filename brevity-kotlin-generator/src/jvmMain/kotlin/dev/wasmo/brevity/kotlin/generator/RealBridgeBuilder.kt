package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.kotlin.encoders.BridgeBuilder
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.FlatEncoder
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.Platform
import dev.wasmo.brevity.kotlin.encoders.byteCount

class RealBridgeBuilder(
  override val bridge: CodeBlock,
  override val nameAllocator: NameAllocator,
  override val code: CodeBlock.Builder,
  override val platform: Platform,
) : BridgeBuilder {
  internal var memoryAllocator: String? = null

  override fun allocate(byteCount: CodeBlock): CodeBlock {
    val memoryAllocator = this@RealBridgeBuilder.memoryAllocator
      ?: nameAllocator.newName("memoryAllocator")
        .also { memoryAllocator = it }
    return platform.allocate(memoryAllocator, byteCount)
  }

  override fun lowerFlat(value: CodeBlock, encoder: Encoder): List<CodeBlock> {
    val encodeBuilder = RealFlatEncoder(mutableListOf(value))
    with(encoder) {
      encodeBuilder.lowerFlat()
    }

    check(encodeBuilder.inputs.isEmpty()) {
      "expected 1 call to take(), but was 0"
    }

    check(encodeBuilder.outputs.size == encoder.coreTypes.size) {
      "expected ${encoder.coreTypes.size} calls to put(), but was ${encodeBuilder.outputs.size}"
    }

    return encodeBuilder.outputs.toList()
  }

  override fun liftFlat(values: List<CodeBlock>, encoder: Encoder): CodeBlock {
    val encodeBuilder = RealFlatEncoder(values.toMutableList())
    with(encoder) {
      encodeBuilder.liftFlat()
    }

    check(encodeBuilder.inputs.isEmpty()) {
      "expected ${values.size} calls to take(), but was ${values.size - encodeBuilder.inputs.size}"
    }

    check(encodeBuilder.outputs.size == 1) {
      "expected 1 call to put(), but was ${encodeBuilder.outputs.size}"
    }

    return encodeBuilder.outputs.single()
  }

  /** When there's multiple core values to return, write them to memory and return a pointer. */
  fun flattenResult(returnValues: List<CodeBlock>, coreResult: CoreResult): CodeBlock {
    val address = nameAllocator.newName("resultAddress")
    allocate(coreResult, address)
    storeResultInMemory(returnValues, coreResult, address)
    return platform.lowerAddress(CodeBlock.of("%N", address))
  }

  /** Allocate space for the encoded value at [address]. */
  fun allocate(coreResult: CoreResult, address: String) {
    val byteCount = coreResult.encoder.coreTypes.sumOf { coreType ->
      coreType.byteCount
    }

    code.addStatement("val %N = %L", address, allocate("%L", byteCount))
  }

  /** Write all of [returnValues] to [address]. */
  fun storeResultInMemory(
    returnValues: List<CodeBlock>,
    coreResult: CoreResult,
    address: String,
  ) {
    val coreTypes = coreResult.encoder.coreTypes

    var offset = 0
    for ((index, value) in returnValues.withIndex()) {
      val coreType = coreTypes[index]
      platform.store(
        baseAddress = CodeBlock.of("%N", address),
        offset = offset,
        type = coreType,
        value = value,
      )
      offset += coreType.byteCount
    }
  }

  /** When an address is returned, unpack the core values from memory. */
  fun unflattenResult(returnValue: CodeBlock, coreResult: CoreResult): List<CodeBlock> {
    val address = nameAllocator.newName("resultAddress")
    code.addStatement("val %N = %L", address, platform.liftAddress(returnValue))

    return loadResultFromMemory(address, coreResult)
  }

  /** Unpack core values from memory. */
  fun loadResultFromMemory(address: String, coreResult: CoreResult): List<CodeBlock> {
    val coreTypes = coreResult.encoder.coreTypes
    val nameHints = coreResult.encoder.nameHints

    var offset = 0
    val result = mutableListOf<CodeBlock>()

    for ((index, coreType) in coreTypes.withIndex()) {
      val nameHint = nameHints?.getOrNull(index)
      val nameSuggestion = when {
        nameHint != null -> Identifier("result-${nameHint}").toCamelCase(upperCamel = false)
        else -> "result"
      }
      val name = nameAllocator.newName(nameSuggestion)
      result += CodeBlock.of("%N", name)

      code.addStatement(
        "val %N = %L",
        name,
        platform.load(
          baseAddress = CodeBlock.of("%N", address),
          offset = offset,
          type = CoreType.I32,
        ),
      )
      offset += coreType.byteCount
    }

    return result
  }

  private inner class RealFlatEncoder(
    val inputs: MutableList<CodeBlock>,
  ) : FlatEncoder, BridgeBuilder by this@RealBridgeBuilder {
    val outputs = mutableListOf<CodeBlock>()

    override fun take(): CodeBlock {
      return inputs.removeFirstOrNull()
        ?: error("unexpected call to take(), input count mismatch?")
    }

    override fun put(value: CodeBlock) {
      outputs += value
    }
  }
}

package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncodeBuilder
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.Platform
import dev.wasmo.brevity.kotlin.encoders.byteCount

/**
 * Generates encode or decode logic for a single receiver, parameter, or return value.
 *
 * When lowering:
 *
 *  * Call [take] once for each [CoreType] that this value is lowered to.
 *  * Call [put] exactly once.
 *
 * When lifting:
 *
 *  * Call [take] exactly once.
 *  * Call [put] once for each [CoreType] that this value is lifted from.
 *
 * Additional statements may be added to [code].
 */
class RealEncodeBuilder(
  override val bridge: CodeBlock,
  override val nameAllocator: NameAllocator,
  override val code: CodeBlock.Builder,
  override val platform: Platform,
) : EncodeBuilder {
  private val inputs = mutableListOf<CodeBlock>()
  private val outputs = mutableListOf<CodeBlock>()
  internal var memoryAllocator: String? = null

  override fun allocate(byteCount: CodeBlock): CodeBlock {
    val memoryAllocator = this.memoryAllocator
      ?: nameAllocator.newName("memoryAllocator")
        .also { memoryAllocator = it }
    return platform.allocate(memoryAllocator, byteCount)
  }

  override fun take(): CodeBlock {
    return inputs.removeFirstOrNull()
      ?: error("unexpected call to take(), input count mismatch?")
  }

  override fun put(value: CodeBlock) {
    outputs += value
  }

  fun lower(value: CodeBlock, encoder: Encoder): List<CodeBlock> {
    inputs += value

    with(encoder) {
      valueToCoreType()
    }

    check(inputs.isEmpty()) {
      "expected 1 call to take(), but was 0"
    }

    check(outputs.size == encoder.coreTypes.size) {
      "expected ${encoder.coreTypes.size} calls to put(), but was ${outputs.size}"
    }

    return outputs.toList()
      .also { outputs.clear() }
  }

  fun lift(values: List<CodeBlock>, encoder: Encoder): CodeBlock {
    inputs += values

    with(encoder) {
      coreTypeToValue()
    }

    check(inputs.isEmpty()) {
      "expected ${values.size} calls to take(), but was ${values.size - inputs.size}"
    }

    check(outputs.size == 1) {
      "expected 1 call to put(), but was ${outputs.size}"
    }

    return outputs.removeFirst()
  }

  /** When there's multiple core values to return, write them to memory and return a pointer. */
  fun flattenResult(returnValues: List<CodeBlock>, encoder: Encoder): CodeBlock {
    val coreTypes = encoder.coreTypes

    val byteCount = coreTypes.sumOf { coreType ->
      coreType.byteCount
    }

    val address = nameAllocator.newName("resultAddress")
    var offset = 0
    code.addStatement("val %N = %L", address, allocate("%L", byteCount))
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

    return platform.lowerAddress(CodeBlock.of("%N", address))
  }

  /** When an address is returned, unpack the core values from memory. */
  fun unflattenResult(returnValue: CodeBlock, encoder: Encoder): List<CodeBlock> {
    val coreTypes = encoder.coreTypes
    val nameHints = encoder.nameHints

    val address = nameAllocator.newName("resultAddress")
    code.addStatement("val %N = %L", address, platform.liftAddress(returnValue))

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
          type = CoreType.I32
        ),
      )
      offset += coreType.byteCount
    }

    return result
  }
}

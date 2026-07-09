package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncodeBuilder
import dev.wasmo.brevity.kotlin.encoders.Encoder
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
) : EncodeBuilder {
  private val inputs = mutableListOf<CodeBlock>()
  private val outputs = mutableListOf<CodeBlock>()
  internal var memoryAllocator: String? = null

  override fun allocate(byteCount: CodeBlock): CodeBlock {
    if (memoryAllocator == null) {
      memoryAllocator = nameAllocator.newName("memoryAllocator")
    }
    return CodeBlock.of("%N.allocate(%L)", memoryAllocator, byteCount)
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

    val pointer = nameAllocator.newName("resultPointer")
    var offset = 0
    code.addStatement("val %N = %L", pointer, allocate("%L", byteCount))
    for ((index, value) in returnValues.withIndex()) {
      val coreType = coreTypes[index]
      val offsetPointer = when {
        offset == 0 -> CodeBlock.of("%N", pointer)
        else -> CodeBlock.of("(%N + %L)", pointer, offset)
      }
      when (coreType) {
        CoreType.I64, CoreType.F64 -> code.addStatement("%L.storeLong(%L)", offsetPointer, value)
        else -> code.addStatement("%L.storeInt(%L)", offsetPointer, value)
      }
      offset += coreType.byteCount
    }

    return CodeBlock.of("%N.address.toInt()", pointer)
  }

  /** When a pointer is returned, unpack the core values from memory. */
  fun unflattenResult(returnValue: CodeBlock, encoder: Encoder): List<CodeBlock> {
    val coreTypes = encoder.coreTypes
    val nameHints = encoder.nameHints

    val pointer = nameAllocator.newName("resultPointer")
    code.addStatement("val %N = %T(%L.toUInt())", pointer, Symbols.KotlinWasm.Pointer, returnValue)

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
      code.addStatement("val %N = (%N + %L).loadInt()", name, pointer, offset)
      offset += coreType.byteCount
    }

    return result
  }
}

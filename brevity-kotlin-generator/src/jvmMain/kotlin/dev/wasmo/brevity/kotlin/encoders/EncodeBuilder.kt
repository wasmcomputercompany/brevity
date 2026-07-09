package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator

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
class EncodeBuilder(
  val bridge: CodeBlock,
  val nameAllocator: NameAllocator,
  val code: CodeBlock.Builder,
) {
  private val inputs = mutableListOf<CodeBlock>()
  private val outputs = mutableListOf<CodeBlock>()
  internal var memoryAllocator: String? = null

  fun allocate(byteCount: CodeBlock): CodeBlock {
    if (memoryAllocator == null) {
      memoryAllocator = nameAllocator.newName("memoryAllocator")
    }
    return CodeBlock.of("%N.allocate(%L)", memoryAllocator, byteCount)
  }

  fun allocate(format: String, vararg args: Any?): CodeBlock = allocate(CodeBlock.of(format, *args))

  fun take(): CodeBlock {
    return inputs.removeFirstOrNull()
      ?: error("unexpected call to take(), input count mismatch?")
  }

  fun put(value: CodeBlock) {
    outputs += value
  }

  fun put(format: String, vararg args: Any?) = put(CodeBlock.of(format, *args))

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
}

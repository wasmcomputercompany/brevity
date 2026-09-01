package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.IoIdentifier
import dev.wasmo.brevity.kotlin.code.CodeBuilder

abstract class Encoder {
  abstract val coreTypes: List<CoreType>
  abstract val byteCount: Int
  abstract val alignment: Int

  open val nameHints: List<IoIdentifier>?
    get() = null

  /** Loads a value from memory at [baseAddress] + [offset]. */
  context(codeBuilder: CodeBuilder)
  abstract fun load(baseAddress: CodeBlock, offset: Int = 0): CodeBlock

  /** Stores [value] in memory at [baseAddress] + [offset]. */
  context(codeBuilder: CodeBuilder)
  abstract fun store(baseAddress: CodeBlock, offset: Int = 0, value: CodeBlock)

  /** Lift an ABI value like a memory address to an API value like a resource instance. */
  context(codeBuilder: CodeBuilder)
  abstract fun liftFlat(transformer: Transformer)

  context(codeBuilder: CodeBuilder)
  fun liftFlat(values: List<CodeBlock>): CodeBlock {
    val transformer = Transformer(values.toMutableList())
    liftFlat(transformer)

    check(transformer.inputs.isEmpty()) {
      "expected ${values.size} calls to take(), but was ${values.size - transformer.inputs.size}"
    }

    check(transformer.outputs.size == 1) {
      "expected 1 call to put(), but was ${transformer.outputs.size}"
    }

    return transformer.outputs.single()
  }

  /** Lower an API value like a resource instance to an ABI value like a memory address. */
  context(codeBuilder: CodeBuilder)
  abstract fun lowerFlat(transformer: Transformer)

  context(codeBuilder: CodeBuilder)
  fun lowerFlat(value: CodeBlock): List<CodeBlock> {
    val transformer = Transformer(mutableListOf(value))
    lowerFlat(transformer)

    check(transformer.inputs.isEmpty()) {
      "expected 1 call to take(), but was 0"
    }

    check(transformer.outputs.size == coreTypes.size) {
      "expected ${coreTypes.size} calls to put(), but was ${transformer.outputs.size}"
    }

    return transformer.outputs.toList()
  }

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
  class Transformer internal constructor(
    internal val inputs: MutableList<CodeBlock>,
  ) {
    internal val outputs = mutableListOf<CodeBlock>()

    fun take(): CodeBlock {
      return inputs.removeFirstOrNull()
        ?: error("unexpected call to take(), input count mismatch?")
    }

    fun put(value: CodeBlock) {
      outputs += value
    }

    fun put(format: String, vararg args: Any?) = put(CodeBlock.of(format, *args))
  }
}

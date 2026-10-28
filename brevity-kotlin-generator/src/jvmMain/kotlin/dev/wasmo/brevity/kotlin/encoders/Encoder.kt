package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.generator.TypeNamer

abstract class Encoder {
  abstract val type: TypeName
  abstract val coreTypes: List<CoreType>
  abstract val byteCount: Int
  abstract val alignment: Int

  /** Returns true if [store] and [lowerFlat]'s `memoryAllocator` parameter must be non-null. */
  open val lowerAllocates: Boolean
    get() = false

  /** Returns true if [lowerFlat] is a standalone function that returns multiple values. */
  open val lowerFlatRequiresCallBuilder: Boolean
    get() = coreTypes.size > 1

  open val nameHints: List<Identifier>?
    get() = null

  /** Loads a value from memory at [baseAddress] + [offset]. */
  context(codeBuilder: CodeBuilder, context: Context)
  abstract fun load(baseAddress: CodeBlock, offset: Int = 0): CodeBlock

  /** Stores [value] in memory at [baseAddress] + [offset]. */
  context(codeBuilder: CodeBuilder, context: Context)
  abstract fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int = 0,
    value: CodeBlock,
  )

  /** Lift an ABI value like a memory address to an API value like a resource instance. */
  context(codeBuilder: CodeBuilder, context: Context)
  abstract fun liftFlat(liftFlatIo: LiftFlatIo)

  context(codeBuilder: CodeBuilder, context: Context)
  fun liftFlat(values: List<CodeBlock>): CodeBlock {
    val liftFlatIo = LiftFlatIo(values.toMutableList())
    liftFlat(liftFlatIo)

    check(liftFlatIo.inputs.isEmpty()) {
      "expected ${values.size} calls to take(), but was ${values.size - liftFlatIo.inputs.size}"
    }

    check(liftFlatIo.outputs.size == 1) {
      "expected 1 call to put(), but was ${liftFlatIo.outputs.size}"
    }

    return liftFlatIo.outputs.single()
  }

  /** Lower an API value like a resource instance to an ABI value like a memory address. */
  context(codeBuilder: CodeBuilder, context: Context)
  abstract fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  )

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
  class LiftFlatIo internal constructor(
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

  /**
   * We have different encoders for resources, based on how they're used. This context can be used
   * to figure out how a resource should be encoded.
   *
   * This is also used to extract reusable functions to encode declared types.
   */
  interface Context {
    val typeNamer: TypeNamer

    /** Returns the orientation of the resource with respect to this function. */
    fun getOrientation(type: TypeName.Declared): Orientation

    context(codeBuilder: CodeBuilder)
    fun lowerFlat(
      encoder: Encoder,
      memoryAllocator: MemoryAllocator?,
      lowerFlatIo: LowerFlatIo,
      value: CodeBlock,
    )

    context(codeBuilder: CodeBuilder)
    fun liftFlat(
      encoder: Encoder,
      values: List<CodeBlock>,
    ): CodeBlock

    context(codeBuilder: CodeBuilder)
    fun load(
      encoder: Encoder,
      baseAddress: CodeBlock,
      offset: Int = 0,
    ): CodeBlock

    context(codeBuilder: CodeBuilder)
    fun store(
      encoder: Encoder,
      memoryAllocator: MemoryAllocator?,
      baseAddress: CodeBlock,
      offset: Int = 0,
      value: CodeBlock,
    )
  }
}

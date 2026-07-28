package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator
import dev.wasmo.brevity.kotlin.encoders.BridgeBuilder
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.FlatEncoder
import dev.wasmo.brevity.kotlin.encoders.Platform

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

  /** Stores [value] at [address]. */
  fun storeValue(address: CodeBlock, value: CodeBlock, coreResult: CoreResult) {
    with (coreResult.encoder) {
      store(address, 0, value)
    }
  }

  /** Loads a value from [address]. */
  fun loadValue(address: CodeBlock, coreResult: CoreResult): CodeBlock {
    return with(coreResult.encoder) {
      load(address, 0)
    }
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

package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.buildCodeBlock
import dev.wasmo.brevity.kotlin.generator.Symbols

internal class GuestEncodeBuilder(
  override val bridge: CodeBlock,
) : EncodeBuilder {
  override val nameAllocator = NameAllocator()
  override val code = CodeBlock.Builder()

  private val inputs = mutableListOf<CodeBlock>()
  private val outputs = mutableListOf<CodeBlock>()

  private var memoryAllocator: String? = null

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

  fun lower(
    value: CodeBlock,
    encoder: Encoder,
    block: Encoder.() -> Unit,
  ): List<CodeBlock> {
    inputs += value
    encoder.block()

    check(inputs.isEmpty()) {
      "expected 1 call to take(), but was 0"
    }

    check(outputs.size == encoder.coreTypes.size) {
      "expected ${encoder.coreTypes.size} calls to put(), but was ${outputs.size}"
    }

    return outputs.toList()
      .also { outputs.clear() }
  }

  fun lift(
    values: List<CodeBlock>,
    encoder: Encoder,
    block: Encoder.() -> Unit,
  ): CodeBlock {
    inputs += values
    encoder.block()

    check(inputs.isEmpty()) {
      "expected ${values.size} calls to take(), but was ${values.size - inputs.size}"
    }

    check(outputs.size == 1) {
      "expected 1 call to put(), but was ${outputs.size}"
    }

    return outputs.removeFirst()
  }

  fun build(): CodeBlock {
    return when {
      memoryAllocator != null -> buildCodeBlock {
        beginControlFlow(
          "%M { %N ->",
          Symbols.KotlinWasm.WithScopedMemoryAllocator,
          memoryAllocator,
        )
        add(code.build())
        endControlFlow()
      }

      else -> code.build()
    }
  }
}

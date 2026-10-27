package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

/** Fake encoder for all the types we don't actually implement yet. */
class FallbackEncoder(
  private val kotlinMapper: KotlinMapper,
  override val type: TypeName,
  val coreType: CoreType,
) : Encoder() {
  override val coreTypes = listOf(coreType)

  override val byteCount: Int
    get() = coreType.byteCount

  override val alignment: Int
    get() = coreType.alignment

  context(codeBuilder: CodeBuilder, context: Context)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of(
    "TODO(%S)",
    "load ${kotlinMapper.get(type)}",
  )

  context(codeBuilder: CodeBuilder, context: Context)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    codeBuilder.addStatement("// TODO: store ${kotlinMapper.get(type)}")
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(transformer: Transformer) {
    transformer.take()
    transformer.put("TODO(%S)", "lift ${kotlinMapper.get(type)}")
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    transformer: Transformer,
  ) {
    transformer.take()
    transformer.put("TODO(%S)", "lower ${kotlinMapper.get(type)}")
  }
}

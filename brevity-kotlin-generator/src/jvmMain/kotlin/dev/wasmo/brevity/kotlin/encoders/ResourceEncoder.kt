package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder

class ResourceEncoder(
  private val type: TypeName.Declared,
) : Encoder() {
  override val coreTypes = listOf(CoreType.I32)

  override val byteCount: Int
    get() = CoreType.I32.byteCount

  override val alignment: Int
    get() = CoreType.I32.alignment

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of("TODO(%S)", "ResourceEncoder")

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    codeBuilder.addStatement("// TODO: ResourceEncoder")
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(codeBuilder.platform.liftResource(transformer.take(), type))
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    transformer.put(codeBuilder.platform.lowerResource(transformer.take(), type))
  }
}

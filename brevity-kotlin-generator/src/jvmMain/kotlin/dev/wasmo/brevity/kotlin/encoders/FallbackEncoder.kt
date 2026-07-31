package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.generator.kotlinApi

/** Fake encoder for all the types we don't actually implement yet. */
class FallbackEncoder(
  private val type: TypeName,
  val coreType: CoreType,
) : Encoder() {
  override val coreTypes = listOf(coreType)

  override val byteCount: Int
    get() = coreType.byteCount

  override val alignment: Int
    get() = coreType.alignment

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of(
    "TODO(%S)",
    "load ${type.kotlinApi}",
  )

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    codeBuilder.addStatement("// TODO: store ${type.kotlinApi}")
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(flatBuilder: FlatBuilder) {
    flatBuilder.take()
    flatBuilder.put("TODO(%S)", "lift ${type.kotlinApi}")
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(flatBuilder: FlatBuilder) {
    flatBuilder.take()
    flatBuilder.put("TODO(%S)", "lower ${type.kotlinApi}")
  }
}

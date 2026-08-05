package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder

class TypeAliasEncoder(
  val type: KtTypeName,
  val delegate: Encoder,
) : Encoder() {
  override val coreTypes: List<CoreType>
    get() = delegate.coreTypes
  override val byteCount: Int
    get() = delegate.byteCount
  override val alignment: Int
    get() = delegate.alignment

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of(
    "%T(%L)",
    type,
    delegate.load(baseAddress, offset),
  )

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    delegate.store(
      baseAddress,
      offset,
      CodeBlock.of("(%L).%N", value, "value"),
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    val codeBlock = delegate.liftFlat(
      values = coreTypes.map { transformer.take() },
    )
    transformer.put(
      "%T(%L)",
      type,
      codeBlock,
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    val values = delegate.lowerFlat(
      CodeBlock.of(
        "(%L).%N",
        transformer.take(),
        "value",
      ),
    )
    for (value in values) {
      transformer.put(value)
    }
  }
}

package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

class TypeAliasEncoder(
  override val brevityTypeName: TypeName,
  val type: KtTypeName,
  val delegate: Encoder,
) : Encoder() {
  override val coreTypes: List<CoreType>
    get() = delegate.coreTypes
  override val byteCount: Int
    get() = delegate.byteCount
  override val alignment: Int
    get() = delegate.alignment
  override val lowerAllocates: Boolean
    get() = delegate.lowerAllocates

  context(codeBuilder: CodeBuilder, context: Context)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = CodeBlock.of(
    "%T(%L)",
    type,
    context.load(
      encoder = delegate,
      baseAddress = baseAddress,
      offset = offset,
    ),
  )

  context(codeBuilder: CodeBuilder, context: Context)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    context.store(
      encoder = delegate,
      memoryAllocator = memoryAllocator,
      baseAddress = baseAddress,
      offset = offset,
      value = CodeBlock.of("(%L).%N", value, "value"),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(transformer: Transformer) {
    val codeBlock = context.liftFlat(
      encoder = delegate,
      values = coreTypes.map { transformer.take() },
    )
    transformer.put(
      "%T(%L)",
      type,
      codeBlock,
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    transformer: Transformer,
  ) {
    val values = context.lowerFlat(
      encoder = delegate,
      memoryAllocator = memoryAllocator,
      value = CodeBlock.of(
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

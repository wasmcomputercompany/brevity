package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

class TypeAliasEncoder(
  override val type: TypeName,
  val ktType: KtTypeName,
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
    ktType,
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
  override fun liftFlat(liftFlatIo: LiftFlatIo) {
    val codeBlock = context.liftFlat(
      encoder = delegate,
      values = coreTypes.map { liftFlatIo.take() },
    )
    liftFlatIo.put(
      "%T(%L)",
      ktType,
      codeBlock,
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  ) {
    context.lowerFlat(
      encoder = delegate,
      memoryAllocator = memoryAllocator,
      lowerFlatIo = lowerFlatIo,
      value = CodeBlock.of("(%L).%N", value, "value"),
    )
  }
}

package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression

/** Abstracts over a platform-specific API to access linear memory. */
interface MemoryAllocator {
  val name: String
  val type: KtTypeName

  /** Allocates [byteCount] bytes of linear memory and returns its address. */
  fun allocate(byteCount: CodeBlock): CodeBlock

  /** Allocates bytes of linear memory and returns its address. */
  fun allocate(format: String, vararg args: Any?): CodeBlock =
    allocate(CodeBlock.of(format, *args))
}


/**
 * Executes [block] with a memory allocator if [lowerAllocates] is true. Otherwise, this executes
 * it inline.
 *
 * Returns the result of block.
 */
context(codeBuilder: CodeBuilder)
fun withMemoryAllocator(
  lowerAllocates: Boolean,
  block: context(CodeBuilder) (MemoryAllocator?) -> KtExpression,
): KtExpression {
  if (!lowerAllocates) {
    return block(null)
  }

  val blockCodeBuilder = CodeBuilder(
    codeBuilder.bridge,
    codeBuilder.platform,
    codeBuilder.nameAllocator.copy(),
  )

  val blockResult = context(blockCodeBuilder) {
    val memoryAllocator = codeBuilder.platform.beginMemoryAllocationScope()
    val blockResult = block(memoryAllocator)

    if (blockResult.type != UNIT) {
      blockCodeBuilder.addStatement("%L", blockResult.code)
    }

    blockCodeBuilder.platform.endMemoryAllocationScope()
    return@context blockResult
  }

  if (blockResult.type == UNIT) {
    codeBuilder.add("%L", blockCodeBuilder.build())
    return KtExpression.Unit
  }

  val resultName = codeBuilder.newName(blockResult.nameHint)
  codeBuilder.add("val %N = %L", resultName, blockCodeBuilder.build())
  return CodeBlockExpression(
    type = blockResult.type,
    nameHint = blockResult.nameHint,
    code = CodeBlock.of("%N", resultName),
    immediate = true,
  )
}

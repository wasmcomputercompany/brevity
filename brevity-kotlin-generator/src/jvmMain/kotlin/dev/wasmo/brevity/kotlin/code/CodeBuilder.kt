package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator

class CodeBuilder(
  val bridge: CodeBlock,
  val nameAllocator: NameAllocator,
  val code: CodeBlock.Builder,
  val platform: Platform,
) {
  internal var memoryAllocator: String? = null

  fun allocate(byteCount: CodeBlock): CodeBlock {
    val memoryAllocator = this.memoryAllocator
      ?: nameAllocator.newName("memoryAllocator")
        .also { this.memoryAllocator = it }
    return platform.allocate(memoryAllocator, byteCount)
  }

  fun allocate(format: String, vararg args: Any?): CodeBlock = allocate(CodeBlock.of(format, *args))

  fun newName(suggestion: String): String = nameAllocator.newName(suggestion)

  fun newName(suggestion: String, tag: Any): String = nameAllocator.newName(suggestion, tag)

  fun beginControlFlow(controlFlow: String, vararg args: Any?) =
    code.beginControlFlow(controlFlow, *args)

  fun addStatement(format: String, vararg args: Any?) =
    code.addStatement(format, *args)

  fun endControlFlow() =
    code.endControlFlow()
}

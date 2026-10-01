package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator

/**
 * Combines a [NameAllocator] and [CodeBlock.Builder] to make generating a lot of code a little
 * easier.
 */
class CodeBuilder(
  val bridge: CodeBlock,
  val platform: Platform,
  nameAllocator: NameAllocator,
) {
  private val code = CodeBlock.Builder()
  private val scopeStack = mutableListOf(nameAllocator)

  val nameAllocator: NameAllocator
    get() = scopeStack.last()

  fun newName(suggestion: String): String = nameAllocator.newName(suggestion)

  fun newName(suggestion: String, tag: Any): String = nameAllocator.newName(suggestion, tag)

  /** Enter a new lexical scope, execute [block], and close that scope. */
  fun <T> controlFlow(
    controlFlow: String,
    vararg args: Any?,
    block: context(CodeBuilder) () -> T,
  ): T {
    beginControlFlow(controlFlow, *args)
    val result = block()
    endControlFlow()
    return result
  }

  fun add(format: String, vararg args: Any?) =
    code.add(format, *args)

  fun addStatement(format: String, vararg args: Any?) =
    code.addStatement(format, *args)

  fun beginControlFlow(format: String, vararg args: Any?) {
    code.beginControlFlow(format, *args)
    scopeStack += nameAllocator.copy()
  }

  fun endControlFlow() {
    code.endControlFlow()
    scopeStack.removeLast()
  }

  fun build(): CodeBlock = code.build()
}

package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator

/**
 * Combines a [NameAllocator] and [CodeBlock.Builder] to make generating a lot of code a little
 * easier.
 *
 * This uses a stack of nested scopes. Each structured control flow has its own name allocator,
 * whose names survive only in that scope.
 *
 * If memory is allocated within a block, the entire block is wrapped in a second block,
 * `withScopedMemoryAllocator`.
 */
class CodeBuilder(
  val bridge: CodeBlock,
  val platform: Platform,
  val nameAllocator: NameAllocator,
) {
  private val memoryAllocatorName = nameAllocator.newName("memoryAllocator")

  /**
   * This contains the current working scope, plus a root scope that's only ever appended to when
   * we call [permitAllocationsNow].
   */
  private val scopeStack = mutableListOf<Scope>(
    MemoryScope(nameAllocator),
    MemoryScope(nameAllocator),
  )

  private val memoryAllocator: MemoryAllocator
    get() = platform.memoryAllocator

  private val scope: Scope
    get() = scopeStack.last()

  fun allocate(byteCount: CodeBlock): CodeBlock {
    this.scope.memoryScope.memoryAllocatorUsed = true
    return memoryAllocator.allocate(bridge, memoryAllocatorName, byteCount)
  }

  fun permitAllocationsNow() {
    check((scope as? MemoryScope)?.memoryAllocatorUsed == false) { "unexpected allocation" }

    popScope()
    pushScope(memoryScope = true)
  }

  fun allocate(format: String, vararg args: Any?): CodeBlock = allocate(CodeBlock.of(format, *args))

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
    scope.code.add(format, *args)

  fun addStatement(format: String, vararg args: Any?) =
    scope.code.addStatement(format, *args)

  fun beginControlFlow(format: String, vararg args: Any?) {
    scope.code.beginControlFlow(format, *args)
    pushScope()
  }

  fun endControlFlow() {
    popScope()
    scope.code.endControlFlow()
  }

  private fun pushScope(memoryScope: Boolean = false) {
    scopeStack += when {
      memoryScope -> MemoryScope(scope.nameAllocator.copy())
      else -> ControlFlowScope(scope.nameAllocator.copy(), scope.memoryScope)
    }
  }

  private fun popScope() {
    val popped = scopeStack.removeLast()
    val parent = scopeStack.last()

    val poppedCode = popped.code.build()

    parent.code.add(
      when {
        (popped as? MemoryScope)?.memoryAllocatorUsed == true -> {
          memoryAllocator.scope(memoryAllocatorName, poppedCode)
        }

        else -> poppedCode
      },
    )
  }

  fun build(): CodeBlock {
    check(scopeStack.size == 2) { "unbalanced begin/end control flow" }
    popScope()
    return scope.code.build()
  }

  private interface Scope {
    val nameAllocator: NameAllocator
    val code: CodeBlock.Builder

    /** The nearest enclosing scope of type [MemoryScope]. */
    val memoryScope: MemoryScope
  }

  /** A scope for naming, but inherits its enclosing scope for allocations. */
  private class ControlFlowScope(
    override val nameAllocator: NameAllocator,
    override val memoryScope: MemoryScope,
  ) : Scope {
    override val code = CodeBlock.Builder()
  }

  /** A scope for naming and allocations. */
  private class MemoryScope(
    override val nameAllocator: NameAllocator,
  ) : Scope {
    override val code = CodeBlock.Builder()
    var memoryAllocatorUsed = false
    override val memoryScope: MemoryScope
      get() = this
  }
}

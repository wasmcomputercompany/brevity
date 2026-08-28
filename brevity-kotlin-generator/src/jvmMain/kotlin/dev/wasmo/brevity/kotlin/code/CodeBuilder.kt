package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.kotlin.generator.Symbols

/**
 * Combines a [NameAllocator] and [CodeBlock.Builder] to make generating a lot of code a little
 * easier.
 *
 * This returns a code block containing up to 2 parts:
 *
 *  - An optional prefix that doesn't allocate anything
 *  - The main code block that may allocate. If it does, that's wrapped in a
 *    `withScopedMemoryAllocator()` block.
 *
 * This split isn't currently particularly typesafe; calling [permitAllocationsNow()] converts
 * the existing code into the prefix.
 */
class CodeBuilder private constructor(
  val bridge: CodeBlock,
  val platform: Platform,
  private val nameAllocator: NameAllocator,
  code: CodeBlock.Builder,
  private val memoryAllocator: MemoryAllocator,
) {
  private val allCode = mutableListOf<CodeBlock>()
  private var code: CodeBlock.Builder = code

  constructor(
    bridge: CodeBlock,
    platform: Platform,
    nameAllocator: NameAllocator,
  ) : this(
    bridge = bridge,
    platform = platform,
    nameAllocator = nameAllocator,
    code = CodeBlock.Builder(),
    memoryAllocator = MemoryAllocator(
      name = nameAllocator.newName("memoryAllocator"),
    ),
  )

  fun allocate(byteCount: CodeBlock): CodeBlock {
    this.memoryAllocator.used = true
    return platform.allocate(memoryAllocator.name, byteCount)
  }

  fun permitAllocationsNow() {
    check(!memoryAllocator.used) { "unexpected allocation" }

    allCode += code.build()
    code = CodeBlock.builder()
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
    code.beginControlFlow(controlFlow, *args)

    val lexicalScope = CodeBuilder(
      bridge = bridge,
      nameAllocator = nameAllocator.copy(),
      code = code,
      platform = platform,
      memoryAllocator = memoryAllocator,
    )

    val result = context(lexicalScope) {
      block()
    }

    code.endControlFlow()

    return result
  }

  fun add(format: String, vararg args: Any?) =
    code.add(format, *args)

  fun addStatement(format: String, vararg args: Any?) =
    code.addStatement(format, *args)

  fun build(): CodeBlock {
    val lastCode = when {
      platform != GuestPlatform -> code.build()

      memoryAllocator.used -> buildCodeBlock {
        beginControlFlow(
          "%M { %N ->",
          Symbols.KotlinWasm.WithScopedMemoryAllocator,
          memoryAllocator.name,
        )
        add(code.build())
        endControlFlow()
      }

      else -> code.build()
    }

    if (allCode.isNotEmpty()) {
      allCode += lastCode
      return allCode.joinToCode(separator = "")
    } else {
      return lastCode
    }
  }

  private class MemoryAllocator(
    val name: String,
  ) {
    var used = false
  }
}

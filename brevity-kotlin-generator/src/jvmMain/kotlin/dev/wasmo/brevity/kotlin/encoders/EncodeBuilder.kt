package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator

/**
 * Generates encode or decode logic for a single receiver, parameter, or return value.
 *
 * When lowering:
 *
 *  * Call [take] once for each [CoreType] that this value is lowered to.
 *  * Call [put] exactly once.
 *
 * When lifting:
 *
 *  * Call [take] exactly once.
 *  * Call [put] once for each [CoreType] that this value is lifted from.
 *
 * Additional statements may be added to [code].
 */
interface EncodeBuilder {
  val bridge: CodeBlock
  val nameAllocator: NameAllocator
  val code: CodeBlock.Builder

  fun allocate(byteCount: CodeBlock): CodeBlock

  fun allocate(format: String, vararg args: Any?): CodeBlock = allocate(CodeBlock.of(format, *args))

  fun take(): CodeBlock

  fun put(value: CodeBlock)

  fun put(format: String, vararg args: Any?) = put(CodeBlock.of(format, *args))
}


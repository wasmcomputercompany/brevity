package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName

/** Abstracts over a platform-specific API to access linear memory. */
interface MemoryAllocator {
  val name: String
  val type: KtTypeName

  /** Allocates [byteCount] bytes of linear memory and returns its address. */
  fun allocate(byteCount: CodeBlock): CodeBlock

  /** Allocates [byteCount] bytes of linear memory and returns its address. */
  fun allocate(format: String, vararg args: Any?): CodeBlock =
    allocate(CodeBlock.of(format, *args))
}

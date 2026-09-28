package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock

/**
 * Generates platform-appropriate code for allocating memory.
 */
interface MemoryAllocator {
  /** Allocates [byteCount] bytes of linear memory and returns its address. */
  fun allocate(
    bridge: CodeBlock,
    memoryAllocatorName: String,
    byteCount: CodeBlock,
  ): CodeBlock

  /** Wraps [body] in a block that can allocate memory. */
  fun scope(memoryAllocatorName: String, body: CodeBlock): CodeBlock
}

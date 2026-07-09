package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock

/**
 * Abstracts over the differences in Wasm APIs like Kotlin/Wasm and Chicory.
 */
interface Platform {
  /** Allocates [byteCount] bytes of linear memory and returns its address. */
  context(builder: EncodeBuilder)
  fun allocate(memoryAllocatorName: String, byteCount: CodeBlock): CodeBlock

  /** Convert an I32 to a pointer. */
  context(builder: EncodeBuilder)
  fun liftAddress(address: CodeBlock): CodeBlock

  /** Convert a pointer to an I32. */
  context(builder: EncodeBuilder)
  fun lowerAddress(address: CodeBlock): CodeBlock

  /** Loads a string from linear memory. */
  context(builder: EncodeBuilder)
  fun loadString(address: CodeBlock, byteCount: CodeBlock): CodeBlock

  /**
   * Allocates a spot for [string] in linear memory and writes it there. Returns the string's
   * address and byte count.
   */
  context(builder: EncodeBuilder)
  fun storeString(string: CodeBlock): Pair<CodeBlock, CodeBlock>

  context(builder: EncodeBuilder)
  fun load(
    baseAddress: CodeBlock,
    offset: Int = 0,
    type: CoreType,
  ): CodeBlock

  context(builder: EncodeBuilder)
  fun store(
    baseAddress: CodeBlock,
    offset: Int = 0,
    type: CoreType,
    value: CodeBlock,
  )
}

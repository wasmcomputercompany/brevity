package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName

/**
 * Abstracts over the differences in Wasm APIs like Kotlin/Wasm and Chicory.
 */
interface Platform {
  /** Allocates [byteCount] bytes of linear memory and returns its address. */
  context(builder: BridgeBuilder)
  fun allocate(memoryAllocatorName: String, byteCount: CodeBlock): CodeBlock

  /** Convert an I32 to a pointer. */
  context(builder: BridgeBuilder)
  fun liftAddress(address: CodeBlock): CodeBlock

  /** Convert a pointer to an I32. */
  context(builder: BridgeBuilder)
  fun lowerAddress(address: CodeBlock): CodeBlock

  /** Convert an ID to a Resource instance. */
  context(builder: BridgeBuilder)
  fun liftResource(id: CodeBlock, handleType: TypeName.Declared): CodeBlock

  /** Convert a Resource instance to an ID. */
  context(builder: BridgeBuilder)
  fun lowerResource(resource: CodeBlock, handleType: TypeName.Declared): CodeBlock

  /** Loads a string from linear memory. */
  context(builder: BridgeBuilder)
  fun loadString(address: CodeBlock, byteCount: CodeBlock): CodeBlock

  /**
   * Allocates a spot for [string] in linear memory and writes it there. Returns the string's
   * address and byte count.
   */
  context(builder: BridgeBuilder)
  fun storeString(string: CodeBlock): Pair<CodeBlock, CodeBlock>

  context(builder: BridgeBuilder)
  fun load(
    baseAddress: CodeBlock,
    offset: Int = 0,
    type: CoreType,
  ): CodeBlock

  context(builder: BridgeBuilder)
  fun store(
    baseAddress: CodeBlock,
    offset: Int = 0,
    type: CoreType,
    value: CodeBlock,
  )
}

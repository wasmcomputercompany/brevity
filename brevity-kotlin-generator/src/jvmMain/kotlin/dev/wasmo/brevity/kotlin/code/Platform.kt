package dev.wasmo.brevity.kotlin.code

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.IntegerType
import dev.wasmo.brevity.kotlin.encoders.integerType

/**
 * Abstracts over the differences in Wasm APIs like Kotlin/Wasm and Chicory.
 */
interface Platform {
  /** Allocates [byteCount] bytes of linear memory and returns its address. */
  context(codeBuilder: CodeBuilder)
  fun allocate(memoryAllocatorName: String, byteCount: CodeBlock): CodeBlock

  /** Convert an I32 to a pointer. */
  fun liftAddress(address: CodeBlock): CodeBlock

  /** Convert a pointer to an I32. */
  fun lowerAddress(address: CodeBlock): CodeBlock

  /** Convert an ID to a Resource instance. */
  context(codeBuilder: CodeBuilder)
  fun liftResource(id: CodeBlock, handleType: TypeName.Declared): CodeBlock

  /** Convert a Resource instance to an ID. */
  context(codeBuilder: CodeBuilder)
  fun lowerResource(resource: CodeBlock, handleType: TypeName.Declared): CodeBlock

  /** Loads a string from linear memory. */
  context(codeBuilder: CodeBuilder)
  fun loadString(address: CodeBlock, byteCount: CodeBlock): CodeBlock

  /**
   * Allocates a spot for [string] in linear memory and writes it there. Returns the string's
   * address and byte count.
   */
  context(codeBuilder: CodeBuilder)
  fun storeString(string: CodeBlock): Pair<CodeBlock, CodeBlock>

  context(codeBuilder: CodeBuilder)
  fun load(
    baseAddress: CodeBlock,
    offset: Int,
    type: CoreType,
  ): CodeBlock = load(baseAddress, offset, type.integerType)

  context(codeBuilder: CodeBuilder)
  fun load(
    baseAddress: CodeBlock,
    offset: Int = 0,
    type: IntegerType,
  ): CodeBlock

  context(codeBuilder: CodeBuilder)
  fun store(
    baseAddress: CodeBlock,
    offset: Int = 0,
    type: CoreType,
    value: CodeBlock,
  ) = store(baseAddress, offset, type.integerType, value)

  context(codeBuilder: CodeBuilder)
  fun store(
    baseAddress: CodeBlock,
    offset: Int = 0,
    type: IntegerType,
    value: CodeBlock,
  )
}

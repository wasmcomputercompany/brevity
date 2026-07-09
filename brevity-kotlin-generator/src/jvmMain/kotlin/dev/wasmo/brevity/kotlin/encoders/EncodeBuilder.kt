package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.NameAllocator

interface EncodeBuilder {
  val bridge: CodeBlock
  val nameAllocator: NameAllocator
  val code: CodeBlock.Builder
  val platform: Platform

  fun allocate(byteCount: CodeBlock): CodeBlock

  fun allocate(format: String, vararg args: Any?): CodeBlock = allocate(CodeBlock.of(format, *args))

  fun take(): CodeBlock

  fun put(value: CodeBlock)

  fun put(format: String, vararg args: Any?) = put(CodeBlock.of(format, *args))
}

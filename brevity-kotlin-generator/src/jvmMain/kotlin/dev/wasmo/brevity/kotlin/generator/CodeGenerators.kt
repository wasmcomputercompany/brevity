package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock

operator fun CodeBlock.plus(offset: Int): CodeBlock {
  return when {
    offset != 0 -> CodeBlock.of("%L + %L", this, offset)
    else -> this
  }
}

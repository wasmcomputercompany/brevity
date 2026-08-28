package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.generator.Symbols

class ByteStringEncoder(
  delegate: Encoder,
) : ConvertingEncoder(delegate) {
  override fun encode(codeBlock: CodeBlock) = CodeBlock.of(
    "%L.toByteArray()",
    codeBlock,
  )

  override fun decode(codeBlock: CodeBlock) = CodeBlock.of(
    "%L.%M()",
    codeBlock,
    Symbols.Okio.ByteStringToByteString,
  )
}

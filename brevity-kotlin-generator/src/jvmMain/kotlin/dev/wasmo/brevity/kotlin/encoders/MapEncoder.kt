package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.generator.Symbols

/**
 * Encode a map<K, V> as a list<tuple<K, V>>
 */
class MapEncoder(
  delegate: Encoder,
) : ConvertingEncoder(delegate) {
  override fun encode(codeBlock: CodeBlock) = CodeBlock.of(
    "%L.toList()",
    codeBlock,
  )

  override fun decode(codeBlock: CodeBlock) = CodeBlock.of(
    "%L.toMap()",
    codeBlock,
  )
}

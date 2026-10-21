package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.kotlin.generator.Symbols

/**
 * Encode a map<K, V> as a list<tuple<K, V>>
 */
class MapEncoder(
  keyType: TypeName,
  valueType: TypeName,
  keyEncoder: Encoder,
  valueEncoder: Encoder,
) : ConvertingEncoder(DynamicListEncoder(
  elementEncoder = PairEncoder(listOf(keyEncoder, valueEncoder)),
  listType = Symbols.KotlinCollections.List.parameterizedBy(
    Symbols.Kotlin.Pair.parameterizedBy(keyType, valueType)
  )
)) {
  override fun encode(codeBlock: CodeBlock) = CodeBlock.of(
    "%L.toList()",
    codeBlock,
  )

  override fun decode(codeBlock: CodeBlock) = CodeBlock.of(
    "%L.toMap()",
    codeBlock,
  )
}

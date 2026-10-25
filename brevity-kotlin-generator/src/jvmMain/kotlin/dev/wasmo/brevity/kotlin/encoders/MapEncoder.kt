package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.generator.Symbols

/**
 * Encode a map<K, V> as a list<tuple<K, V>>
 */
class MapEncoder(
  keyType: KtTypeName,
  valueType: KtTypeName,
  keyEncoder: Encoder,
  valueEncoder: Encoder,
) : ConvertingEncoder(
  DynamicListEncoder(
    elementEncoder = PairEncoder(listOf(keyEncoder, valueEncoder)),
    listType = Symbols.KotlinCollections.List.parameterizedBy(
      Symbols.Kotlin.Pair.parameterizedBy(keyType, valueType),
    ),
  ),
) {
  override val type: TypeName.Map =
    TypeName.Map(keyEncoder.type, valueEncoder.type)

  override fun encode(codeBlock: CodeBlock) = CodeBlock.of(
    "%L.toList()",
    codeBlock,
  )

  override fun decode(codeBlock: CodeBlock) = CodeBlock.of(
    "%L.toMap()",
    codeBlock,
  )
}

package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.buildCodeBlock
import dev.wasmo.brevity.kotlin.generator.Symbols

/**
 * A list whose size is statically specified in `.wit`. This is also used for tuples of length
 * greater than `Quad` with homogenous element types.
 */
class StaticListEncoder(
  size: Int,
  private val elementType: TypeName,
  elementEncoder: Encoder,
) : AbstractRecordEncoder(List(size) { elementEncoder }) {
  override val instanceNameHint: String
    get() = "list"

  override fun fieldValuesToInstance(
    fieldValues: List<CodeBlock>,
  ) = buildCodeBlock {
    add(
      "%M<%T>(⇥\n",
      Symbols.KotlinCollections.ListOf,
      elementType,
    )
    for (fieldValue in fieldValues) {
      add("%L,\n", fieldValue)
    }
    add("⇤)")
  }

  override fun instanceToFieldValues(
    record: CodeBlock,
  ) = fieldEncoders.withIndex().map { (i, _) ->
    CodeBlock.of("%L[%L]", record, i)
  }
}

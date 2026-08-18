package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import dev.wasmo.brevity.kotlin.generator.Symbols

class PairEncoder(
  fieldEncoders: List<Encoder>,
) : AbstractRecordEncoder(fieldEncoders) {
  override val instanceNameHint: String
    get() = "pair"

  override fun fieldValuesToInstance(
    fieldValues: List<CodeBlock>,
  ) = CodeBlock.of(
    "(%L to %L)",
    fieldValues[0],
    fieldValues[1],
  )

  override fun instanceToFieldValues(
    record: CodeBlock,
  ) = listOf(
    CodeBlock.of("%L.first", record),
    CodeBlock.of("%L.second", record),
  )
}

class TripleEncoder(
  fieldEncoders: List<Encoder>,
) : AbstractRecordEncoder(fieldEncoders) {
  override val instanceNameHint: String
    get() = "triple"

  override fun fieldValuesToInstance(
    fieldValues: List<CodeBlock>,
  ) = CodeBlock.of(
    "%T(%L, %L, %L)",
    Symbols.Kotlin.Triple,
    fieldValues[0],
    fieldValues[1],
    fieldValues[2],
  )

  override fun instanceToFieldValues(
    record: CodeBlock,
  ) = listOf(
    CodeBlock.of("%L.first", record),
    CodeBlock.of("%L.second", record),
    CodeBlock.of("%L.third", record),
  )
}

class QuadEncoder(
  fieldEncoders: List<Encoder>,
) : AbstractRecordEncoder(fieldEncoders) {
  override val instanceNameHint: String
    get() = "quad"

  override fun fieldValuesToInstance(
    fieldValues: List<CodeBlock>,
  ) = CodeBlock.of(
    "%T(%L, %L, %L, %L)",
    Symbols.Brevity.Quad,
    fieldValues[0],
    fieldValues[1],
    fieldValues[2],
    fieldValues[3],
  )

  override fun instanceToFieldValues(
    record: CodeBlock,
  ) = listOf(
    CodeBlock.of("%L.a", record),
    CodeBlock.of("%L.b", record),
    CodeBlock.of("%L.c", record),
    CodeBlock.of("%L.d", record),
  )
}

/** 5 or more heterogeneous elements. */
class LargeTupleEncoder(
  private val elementTypes: List<KtTypeName>,
  fieldEncoders: List<Encoder>,
) : AbstractRecordEncoder(fieldEncoders) {
  override val instanceNameHint: String
    get() = "tuple"

  override fun fieldValuesToInstance(
    fieldValues: List<CodeBlock>,
  ) = buildCodeBlock {
    add("%M(⇥\n", Symbols.KotlinCollections.ListOf)
    for (fieldValue in fieldValues) {
      add("%L,\n", fieldValue)
    }
    add("⇤)")
  }

  override fun instanceToFieldValues(
    record: CodeBlock,
  ) = fieldEncoders.withIndex().map { (i, _) ->
    CodeBlock.of("(%L[%L] as %T)", record, i, elementTypes[i])
  }
}

package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import dev.wasmo.brevity.kotlin.generator.Symbols

class PairEncoder(
  fieldEncoders: List<Encoder>,
) : AbstractRecordEncoder(fieldEncoders) {
  override fun fieldValuesToInstance(
    fieldValues: List<CodeBlock>,
  ) = CodeBlock.of(
    "(%L to %L)",
    fieldValues[0],
    fieldValues[1],
  )

  override fun instanceToFieldValues(
    tuple: CodeBlock,
  ) = listOf(
    CodeBlock.of("%L.first", tuple),
    CodeBlock.of("%L.second", tuple),
  )
}

class TripleEncoder(
  fieldEncoders: List<Encoder>,
) : AbstractRecordEncoder(fieldEncoders) {
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
    tuple: CodeBlock,
  ) = listOf(
    CodeBlock.of("%L.first", tuple),
    CodeBlock.of("%L.second", tuple),
    CodeBlock.of("%L.third", tuple),
  )
}

class QuadEncoder(
  fieldEncoders: List<Encoder>,
) : AbstractRecordEncoder(fieldEncoders) {
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
    tuple: CodeBlock,
  ) = listOf(
    CodeBlock.of("%L.a", tuple),
    CodeBlock.of("%L.b", tuple),
    CodeBlock.of("%L.c", tuple),
    CodeBlock.of("%L.d", tuple),
  )
}

/** 5 or more elements. */
class LargeTupleEncoder(
  fieldEncoders: List<Encoder>,
) : AbstractRecordEncoder(fieldEncoders) {
  override fun fieldValuesToInstance(
    fieldValues: List<CodeBlock>,
  ) = buildCodeBlock {
    add("%M(⇥\n", Symbols.KotlinCollections.ListOf)
    for (fieldValue in fieldValues) {
      add("%L,\n", fieldValue)
    }
    add("⇤)", Symbols.KotlinCollections.ListOf)
  }

  override fun instanceToFieldValues(
    tuple: CodeBlock,
  ) = fieldEncoders.withIndex().map { (i, _) ->
    CodeBlock.of("%L[%L]", tuple, i)
  }
}

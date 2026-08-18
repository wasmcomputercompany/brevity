package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import dev.wasmo.brevity.ir.IrField
import dev.wasmo.brevity.kotlin.generator.kotlinName

class RecordEncoder(
  private val kotlinType: ClassName,
  override val instanceNameHint: String,
  private val fields: List<IrField>,
  fieldEncoders: List<Encoder>,
) : AbstractRecordEncoder(fieldEncoders) {
  override fun fieldValuesToInstance(
    fieldValues: List<CodeBlock>,
  ) = buildCodeBlock {
    add("%T(⇥\n", kotlinType)
    for ((i, fieldValue) in fieldValues.withIndex()) {
      add("%N = %L,\n", fields[i].kotlinName, fieldValue)
    }
    add("⇤)")
  }

  override fun instanceToFieldValues(
    record: CodeBlock,
  ) = fields.map { field ->
    CodeBlock.of("%L.%N", record, field.kotlinName)
  }
}

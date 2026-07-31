package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder

abstract class AbstractRecordEncoder(
  protected val fieldEncoders: List<Encoder>,
) : Encoder() {
  override val coreTypes = fieldEncoders.flatMap { it.coreTypes }

  override val byteCount: Int
    get() = fieldEncoders.sumOf { it.byteCount }

  override val alignment: Int
    get() = fieldEncoders.maxOf { it.alignment }

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = fieldValuesToInstance(
    fieldValues = buildList {
      var offset = offset
      for (fieldEncoder in fieldEncoders) {
        offset = offset.alignTo(fieldEncoder.alignment)
        add(
          fieldEncoder.load(
            baseAddress = baseAddress,
            offset = offset,
          ),
        )
        offset += fieldEncoder.byteCount
      }
    },
  )

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val fieldValues = instanceToFieldValues(value)
    var offset = offset
    for ((index, fieldEncoder) in fieldEncoders.withIndex()) {
      offset = offset.alignTo(fieldEncoder.alignment)
      fieldEncoder.store(
        baseAddress = baseAddress,
        offset = offset,
        value = fieldValues[index],
      )
      offset += fieldEncoder.byteCount
    }
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(
      fieldValuesToInstance(
        fieldValues = fieldEncoders.map { fieldEncoder ->
          fieldEncoder.liftFlat(
            values = fieldEncoder.coreTypes.map { transformer.take() },
          )
        },
      ),
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    val tuple = codeBuilder.newName("tuple")
    codeBuilder.addStatement("val %N = %L", tuple, transformer.take())

    val fieldValues = instanceToFieldValues(CodeBlock.of("%N", tuple))
    for ((i, fieldEncoder) in fieldEncoders.withIndex()) {
      for (coreType in fieldEncoder.lowerFlat(fieldValues[i])) {
        transformer.put(coreType)
      }
    }
  }

  abstract fun fieldValuesToInstance(fieldValues: List<CodeBlock>): CodeBlock

  abstract fun instanceToFieldValues(tuple: CodeBlock): List<CodeBlock>
}

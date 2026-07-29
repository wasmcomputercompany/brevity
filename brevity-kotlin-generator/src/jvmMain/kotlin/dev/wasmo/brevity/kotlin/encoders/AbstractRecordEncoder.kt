package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock

abstract class AbstractRecordEncoder(
  protected val fieldEncoders: List<Encoder>,
) : Encoder() {
  override val coreTypes = fieldEncoders.flatMap { it.coreTypes }

  override val byteCount: Int
    get() = fieldEncoders.sumOf { it.byteCount }

  override val alignment: Int
    get() = fieldEncoders.maxOf { it.alignment }

  override fun BridgeBuilder.load(
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    return fieldValuesToInstance(
      fieldValues = buildList {
        var offset = offset
        for (fieldEncoder in fieldEncoders) {
          offset = offset.alignTo(fieldEncoder.alignment)
          with(fieldEncoder) {
            add(load(baseAddress, offset))
          }
          offset += fieldEncoder.byteCount
        }
      },
    )
  }

  override fun BridgeBuilder.store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val fieldValues = instanceToFieldValues(value)
    var offset = offset
    for ((index, fieldEncoder) in fieldEncoders.withIndex()) {
      offset = offset.alignTo(fieldEncoder.alignment)
      with(fieldEncoder) {
        store(baseAddress, offset, fieldValues[index])
      }
      offset += fieldEncoder.byteCount
    }
  }

  override fun FlatEncoder.liftFlat() {
    put(
      fieldValuesToInstance(
        fieldValues = fieldEncoders.map { fieldEncoder ->
          liftFlat(
            values = fieldEncoder.coreTypes.map { take() },
            encoder = fieldEncoder,
          )
        },
      ),
    )
  }

  override fun FlatEncoder.lowerFlat() {
    val tuple = nameAllocator.newName("tuple")
    code.addStatement("val %N = %L", tuple, take())

    val fieldValues = instanceToFieldValues(CodeBlock.of("%N", tuple))
    for ((i, fieldEncoder) in fieldEncoders.withIndex()) {
      for (coreType in lowerFlat(fieldValues[i], fieldEncoder)) {
        put(coreType)
      }
    }
  }

  abstract fun fieldValuesToInstance(fieldValues: List<CodeBlock>): CodeBlock

  abstract fun instanceToFieldValues(tuple: CodeBlock): List<CodeBlock>
}

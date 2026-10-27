package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

abstract class AbstractRecordEncoder(
  protected val fieldEncoders: List<Encoder>,
) : Encoder() {
  override val coreTypes = fieldEncoders.flatMap { it.coreTypes }

  /** Each field's offset from the start of the record, padded to its alignment. */
  private val fieldOffsets: List<Int>

  /** Total bytes this record takes up, including padding. */
  final override val byteCount: Int

  override val lowerFlatRequiresCallBuilder: Boolean
    get() = (type is TypeName.Declared && coreTypes.size > 1) ||
      fieldEncoders.any { it.lowerFlatRequiresCallBuilder }

  init {
    var offset = 0
    fieldOffsets = buildList {
      fieldEncoders.forEach { encoder ->
        offset = offset.alignTo(encoder.alignment)
        add(offset)
        offset += encoder.byteCount
      }
    }
    byteCount = offset.alignTo(alignment)
  }

  final override val alignment: Int
    get() = fieldEncoders.maxOf { it.alignment }

  abstract val instanceNameHint: String

  override val lowerAllocates: Boolean
    get() = fieldEncoders.any { it.lowerAllocates }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = fieldValuesToInstance(
    fieldValues = loadAll(baseAddress, offset),
  )

  context(codeBuilder: CodeBuilder, context: Context)
  fun loadAll(
    baseAddress: CodeBlock,
    offset: Int = 0,
  ) = buildList {
    fieldEncoders.forEachIndexed { index, fieldEncoder ->
      add(
        context.load(
          encoder = fieldEncoder,
          baseAddress = baseAddress,
          offset = offset + fieldOffsets[index],
        ),
      )
    }
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun store(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    storeAll(
      memoryAllocator = memoryAllocator,
      baseAddress = baseAddress,
      offset = offset,
      fieldValues = instanceToFieldValues(value),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  fun storeAll(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int = 0,
    fieldValues: List<CodeBlock>,
  ) {
    fieldEncoders.forEachIndexed { index, fieldEncoder ->
      context.store(
        encoder = fieldEncoder,
        memoryAllocator = memoryAllocator,
        baseAddress = baseAddress,
        offset = offset + fieldOffsets[index],
        value = fieldValues[index],
      )
    }
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun liftFlat(liftFlatIo: LiftFlatIo) {
    liftFlatIo.put(
      fieldValuesToInstance(
        fieldValues = fieldEncoders.map { fieldEncoder ->
          context.liftFlat(
            encoder = fieldEncoder,
            values = fieldEncoder.coreTypes.map { liftFlatIo.take() },
          )
        },
      ),
    )
  }

  context(codeBuilder: CodeBuilder, context: Context)
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  ) {
    val nameHint = codeBuilder.newName(instanceNameHint)
    codeBuilder.addStatement("val %N = %L", nameHint, value)

    val fieldValues = instanceToFieldValues(CodeBlock.of("%N", nameHint))
    check(fieldEncoders.size == fieldValues.size)
    for ((fieldEncoder, value) in fieldEncoders.zip(fieldValues)) {
      context.lowerFlat(fieldEncoder, memoryAllocator, lowerFlatIo, value)
    }
  }

  abstract fun fieldValuesToInstance(fieldValues: List<CodeBlock>): CodeBlock

  abstract fun instanceToFieldValues(record: CodeBlock): List<CodeBlock>
}

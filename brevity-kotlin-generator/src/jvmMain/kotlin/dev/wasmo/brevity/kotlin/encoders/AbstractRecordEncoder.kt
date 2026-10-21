package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator

abstract class AbstractRecordEncoder(
  protected val fieldEncoders: List<Encoder>,
) : Encoder() {
  override val coreTypes = fieldEncoders.flatMap { it.coreTypes }

  /** Each field's offset from the start of the record, padded to its alignment. */
  private val fieldOffsets: List<Int>

  /** Total bytes this record takes up, including padding. */
  final override val byteCount: Int

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

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = fieldValuesToInstance(
    fieldValues = loadAll(baseAddress, offset),
  )

  context(codeBuilder: CodeBuilder)
  fun loadAll(
    baseAddress: CodeBlock,
    offset: Int = 0,
  ) = buildList {
    fieldEncoders.forEachIndexed { index, fieldEncoder ->
      add(
        fieldEncoder.load(
          baseAddress = baseAddress,
          offset = offset + fieldOffsets[index],
        ),
      )
    }
  }

  context(codeBuilder: CodeBuilder)
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

  context(codeBuilder: CodeBuilder)
  fun storeAll(
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int = 0,
    fieldValues: List<CodeBlock>,
  ) {
    fieldEncoders.forEachIndexed { index, fieldEncoder ->
      fieldEncoder.store(
        memoryAllocator = memoryAllocator,
        baseAddress = baseAddress,
        offset = offset + fieldOffsets[index],
        value = fieldValues[index],
      )
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
  override fun lowerFlat(
    memoryAllocator: MemoryAllocator?,
    transformer: Transformer,
  ) {
    val nameHint = codeBuilder.newName(instanceNameHint)
    codeBuilder.addStatement("val %N = %L", nameHint, transformer.take())

    val fieldValues = instanceToFieldValues(CodeBlock.of("%N", nameHint))
    for ((i, fieldEncoder) in fieldEncoders.withIndex()) {
      for (coreType in fieldEncoder.lowerFlat(memoryAllocator, fieldValues[i])) {
        transformer.put(coreType)
      }
    }
  }

  abstract fun fieldValuesToInstance(fieldValues: List<CodeBlock>): CodeBlock

  abstract fun instanceToFieldValues(record: CodeBlock): List<CodeBlock>
}

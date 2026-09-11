package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.ir.IrFlag
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.generator.kotlinName

/**
 * Flags are implemented as packed integer values. This encoder packs and unpacks values.
 */
class FlagsEncoder(
  private val kotlinType: ClassName,
  private val flags: List<IrFlag>,
  private val packedFlagEncoder: DirectEncoder,
) : Encoder() {
  override val coreTypes = packedFlagEncoder.coreTypes

  override val byteCount: Int = packedFlagEncoder.byteCount

  override val alignment: Int = packedFlagEncoder.alignment

  context(codeBuilder: CodeBuilder)
  override fun load(
    baseAddress: CodeBlock,
    offset: Int,
  ) = packedValueToInstance(packedFlagEncoder.load(baseAddress, offset))

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val packedFlagsName = codeBuilder.newName("packedFlags")

    codeBuilder.addStatement(
      "val %N = %L", packedFlagsName,
      instanceToPackedValue(value)
    )

    packedFlagEncoder.store(
      baseAddress = baseAddress,
      offset = offset,
      value = CodeBlock.of("%N", packedFlagsName)
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(packedValueToInstance(transformer.take()))
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    transformer.put(instanceToPackedValue(transformer.take()))
  }

  context(codeBuilder: CodeBuilder)
  private fun packedValueToInstance(packedValue: CodeBlock): CodeBlock {
    return buildCodeBlock {
      val packedFlagsName = codeBuilder.newName("packedFlags")

      add("%L.toInt().let { %N -> %T(⇥\n", packedValue, packedFlagsName, kotlinType)
      for ((i, flag) in flags.withIndex()) {
        add("%N = %N and ${1 shl i} != 0,\n", flag.kotlinName, packedFlagsName)
      }
      add("⇤)}\n")
    }
  }

  private fun instanceToPackedValue(valueName: CodeBlock): CodeBlock = flags.mapIndexed { i, flag ->
    CodeBlock.of("(if (%L.%N) ${1 shl i} else 0)", valueName, flag.kotlinName)
  }.joinToCode(" or \n")

}

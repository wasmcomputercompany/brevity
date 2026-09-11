package dev.wasmo.brevity.kotlin.encoders

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.ir.IrFlag
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.generator.kotlinName

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
  ) = buildCodeBlock {
    val packedFlagsName = codeBuilder.newName("packedFlags")

    add("%L.toInt().let { %N -> %T(⇥\n", packedFlagEncoder.load(baseAddress, offset), packedFlagsName, kotlinType)
    for ((i, flag) in flags.withIndex()) {
      add("%N = %N and (1 shl $i) != 0,\n", flag.kotlinName, packedFlagsName )
    }
    add("⇤)}\n")
  }

  context(codeBuilder: CodeBuilder)
  override fun store(
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val packedFlagsName = codeBuilder.newName("packedFlags")

    codeBuilder.addStatement(
      "val %N = %L", packedFlagsName,
      flags.mapIndexed { i, flag ->
        CodeBlock.of("(if (%L.%N) 1 shl $i else 0)", value, flag.kotlinName)
      }.joinToCode(" or \n"))

    packedFlagEncoder.store(
      baseAddress = baseAddress,
      offset = offset,
      value = CodeBlock.of("%N", packedFlagsName)
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(transformer: Transformer) {
    transformer.put(
      buildCodeBlock {
        val packedFlagsName = codeBuilder.newName("packedFlags")

        add("%L.toInt().let { %N -> %T(⇥\n", transformer.take(), packedFlagsName, kotlinType)
        for ((i, flag) in flags.withIndex()) {
          add("%N = %N and (1 shl $i) != 0,\n", flag.kotlinName, packedFlagsName )
        }
        add("⇤)}\n")
      },
    )
  }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(transformer: Transformer) {
    val valueName = transformer.take()
    // TODO: Determine if this needs to be in a local val.
    transformer.put(
      flags.mapIndexed { i, flag ->
        CodeBlock.of("(if (%L.%N) 1 shl $i else 0)", valueName, flag.kotlinName)
      }.joinToCode(" or \n"))
  }

}

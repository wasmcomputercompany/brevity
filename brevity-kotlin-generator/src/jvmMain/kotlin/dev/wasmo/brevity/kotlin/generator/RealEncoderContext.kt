package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.abi.AbiFunction
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.encoders.Encoder

class RealEncoderContext(
  private val collector: EncoderFunctionCollector,
  private val function: AbiFunction,
) : Encoder.Context {
  override val typeNamer: TypeNamer
    get() = collector.typeNamer

  /** Returns the orientation of the resource with respect to this function. */
  override fun getOrientation(type: TypeName.Declared): Orientation {
    return when {
      function.orientation == Export && resourceInterfaceIsExported(type) -> Export
      else -> Orientation.Import
    }
  }

  private fun resourceInterfaceIsExported(type: TypeName.Declared): Boolean =
    collector.world.interfaces.any { abiInterface ->
      abiInterface.serviceName == type.serviceName && abiInterface.orientation == Export
    }

  context(codeBuilder: CodeBuilder)
  override fun lowerFlat(
    encoder: Encoder,
    value: CodeBlock,
    memoryAllocator: MemoryAllocator?,
  ): List<CodeBlock> {
    val toLower = collector.kotlinMapper.typeMapper(encoder.type).toWit(value)
    return context(this) {
      encoder.lowerFlat(memoryAllocator, toLower)
    }
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(
    encoder: Encoder,
    values: List<CodeBlock>,
  ): CodeBlock {
    val lifted = context(this) {
      encoder.liftFlat(values)
    }
    return collector.kotlinMapper.typeMapper(encoder.type).fromWit(lifted)
  }

  context(codeBuilder: CodeBuilder)
  override fun load(
    encoder: Encoder,
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    val typeMapper = collector.kotlinMapper.typeMapper(encoder.type)
    return when (encoder.type) {
      is TypeName.Declared -> {
        collector.callDeclaredLoadFunction(
          encoderContext = this,
          encoder = encoder,
          typeMapper = typeMapper,
          orientation = function.orientation,
          baseAddress = baseAddress,
          offset = offset,
        )
      }

      else -> context(this) {
        typeMapper.fromWit(encoder.load(baseAddress, offset))
      }
    }
  }

  context(codeBuilder: CodeBuilder)
  override fun store(
    encoder: Encoder,
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val toStore = collector.kotlinMapper.typeMapper(encoder.type).toWit(value)
    context(this) {
      encoder.store(memoryAllocator, baseAddress, offset, toStore)
    }
  }
}

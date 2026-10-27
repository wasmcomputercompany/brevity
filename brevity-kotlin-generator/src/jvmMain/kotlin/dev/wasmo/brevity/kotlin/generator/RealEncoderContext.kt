package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.abi.AbiFunction
import dev.wasmo.brevity.kotlin.code.LowerFlatIo
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
    memoryAllocator: MemoryAllocator?,
    lowerFlatIo: LowerFlatIo,
    value: CodeBlock,
  ) {
    val typeMapper = collector.kotlinMapper.typeMapper(encoder.type)
    return when (encoder.type) {
      is TypeName.Declared -> {
        collector.callDeclaredLowerFlatFunction(
          encoderContext = this,
          encoder = encoder,
          typeMapper = typeMapper,
          orientation = function.orientation,
          memoryAllocator = memoryAllocator,
          lowerFlatIo = lowerFlatIo,
          value = value,
        )
      }

      else -> context(this) {
        val toLower = typeMapper.toWit(value)
        encoder.lowerFlat(memoryAllocator, lowerFlatIo, toLower)
      }
    }
  }

  context(codeBuilder: CodeBuilder)
  override fun liftFlat(
    encoder: Encoder,
    values: List<CodeBlock>,
  ): CodeBlock {
    val typeMapper = collector.kotlinMapper.typeMapper(encoder.type)
    return when (encoder.type) {
      is TypeName.Declared -> {
        collector.callDeclaredLiftFlatFunction(
          encoderContext = this,
          encoder = encoder,
          typeMapper = typeMapper,
          orientation = function.orientation,
          values = values,
        )
      }

      else -> context(this) {
        typeMapper.fromWit(encoder.liftFlat(values))
      }
    }
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
    val typeMapper = collector.kotlinMapper.typeMapper(encoder.type)
    when (encoder.type) {
      is TypeName.Declared -> {
        collector.callDeclaredStoreFunction(
          encoderContext = this,
          encoder = encoder,
          typeMapper = typeMapper,
          orientation = function.orientation,
          memoryAllocator = memoryAllocator,
          baseAddress = baseAddress,
          offset = offset,
          value = value,
        )
      }

      else -> {
        val toStore = typeMapper.toWit(value)
        context(this) {
          encoder.store(memoryAllocator, baseAddress, offset, toStore)
        }
      }
    }
  }
}

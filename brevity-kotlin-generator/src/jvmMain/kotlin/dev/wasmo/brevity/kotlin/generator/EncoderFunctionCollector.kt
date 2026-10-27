package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName as KtTypeName
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.Orientation.Export
import dev.wasmo.brevity.Orientation.Import
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.abi.AbiFunction
import dev.wasmo.brevity.kotlin.abi.AbiWorld
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.encoders.Encoder

class EncoderFunctionCollector(
  val kotlinMapper: KotlinMapper,
  val qualifiedSpecCollector: QualifiedSpecCollector,
  val world: AbiWorld,
  val typeNamer: TypeNamer,
)

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
      else -> Import
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
    val toLower = transform(encoder).toWit(value)
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
    return transform(encoder).fromWit(lifted)
  }

  context(codeBuilder: CodeBuilder)
  override fun load(
    encoder: Encoder,
    baseAddress: CodeBlock,
    offset: Int,
  ): CodeBlock {
    val loaded = context(this) {
      encoder.load(baseAddress, offset)
    }
    return transform(encoder).fromWit(loaded)
  }

  context(codeBuilder: CodeBuilder)
  override fun store(
    encoder: Encoder,
    memoryAllocator: MemoryAllocator?,
    baseAddress: CodeBlock,
    offset: Int,
    value: CodeBlock,
  ) {
    val toStore = transform(encoder).toWit(value)
    context(this) {
      encoder.store(memoryAllocator, baseAddress, offset, toStore)
    }
  }

  private fun transform(encoder: Encoder): Transform {
    val typeName = encoder.type as? TypeName.Declared ?: return Transform.Identity
    val kotlinMapper = collector.kotlinMapper
    val customType = kotlinMapper.customTypeMappings[typeName] ?: return Transform.Identity
    return MappingTransform(
      kotlinMapper = kotlinMapper,
      type = typeName,
      customType = customType,
    )
  }

  private interface Transform {
    context(codeBuilder: CodeBuilder)
    fun toWit(value: CodeBlock): CodeBlock

    context(codeBuilder: CodeBuilder)
    fun fromWit(value: CodeBlock): CodeBlock

    object Identity : Transform {
      context(codeBuilder: CodeBuilder)
      override fun toWit(value: CodeBlock) = value

      context(codeBuilder: CodeBuilder)
      override fun fromWit(value: CodeBlock) = value
    }
  }

  private class MappingTransform(
    val kotlinMapper: KotlinMapper,
    val type: TypeName.Declared,
    val customType: KtTypeName?,
  ) : Transform {
    /** Returns a code block with [value] converted to its ABI type, if necessary. */
    context(codeBuilder: CodeBuilder)
    override fun toWit(value: CodeBlock): CodeBlock {
      return when {
        customType != null -> {
          CodeBlock.of("%L", codeBuilder.newName("witValue"))
            .also {
              codeBuilder.addStatement(
                "val %L = %M.toWit(%L)",
                it,
                kotlinMapper.getAdaptersObjectMemberName(type),
                value,
              )
            }
        }

        else -> value
      }
    }

    /** Returns a code block with [value] converted from its ABI type, if necessary. */
    context(codeBuilder: CodeBuilder)
    override fun fromWit(value: CodeBlock): CodeBlock {
      return when {
        customType != null -> CodeBlock.of(
          "%M.fromWit(%L)",
          kotlinMapper.getAdaptersObjectMemberName(type),
          value,
        )

        else -> value
      }
    }
  }
}

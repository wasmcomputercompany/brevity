package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.Orientation
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.abi.AbiFunction
import dev.wasmo.brevity.kotlin.abi.AbiStream
import dev.wasmo.brevity.kotlin.abi.AbiWorld
import dev.wasmo.brevity.kotlin.abi.WasmImportFunction
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.expressions.valueExpression

internal class StreamBindingGenerator(
  private val encoderFunctionCollector: EncoderFunctionCollector,
  private val streamBindingsCollector: StreamBindingsCollector,
  private val platform: Platform,
  kotlinMapper: KotlinMapper,
  typeNamer: TypeNamer,
  private val bridge: CodeBlock,
  private val abiWorld: AbiWorld,
  private val abiStream: AbiStream,
) {
  private val streamType = abiStream.irStream.type
  private val streamTypeKt = Symbols.Brevity.streamBindingType(abiStream.cardinality)
  private val elementTypeKt = streamType.type?.let { kotlinMapper.get(it) } ?: UNIT

  private val bindingSuperclass = streamTypeKt.parameterizedBy(elementTypeKt)

  val className = typeNamer.streamImplementation(streamType)

  fun generateGuest(): TypeSpec {
    return TypeSpec.objectBuilder(className)
      .addModifiers(KModifier.INTERNAL)
      .superclass(bindingSuperclass)
      .addProperty(byteCountProperty())
      .apply {
        for (abiFunction in abiStream.functions) {
          addFunction(guestFunction(abiFunction))
        }
      }
      .addFunction(storeFunction())
      .addFunction(loadFunction())
      .build()
  }

  fun generateHost(): TypeSpec {
    return TypeSpec.classBuilder(className)
      .addModifiers(KModifier.INNER)
      .superclass(bindingSuperclass)
      .addProperty(byteCountProperty())
      .addFunction(storeFunction())
      .addFunction(loadFunction())
      .build()
  }

  private fun guestFunction(abiFunction: AbiFunction): FunSpec {
    val functionNameKt = when (val functionName = abiFunction.name) {
      is FunctionName.StreamCancel -> "cancel${functionName.op.verb.upperCamelCase}"
      is FunctionName.StreamDrop -> "drop${functionName.op.verb.upperCamelCase}"
      is FunctionName.StreamIo -> functionName.op.verb.lowerCamelCase
      is FunctionName.StreamNew -> "new"
      else -> error("unexpected function name: $functionName")
    }

    return FunSpec.builder(functionNameKt)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        for (spec in abiFunction.parameters.liftedSpecs) {
          addParameter(spec)
        }

        val codeBuilder = CodeBuilder(
          bridge = bridge,
          platform = platform,
          nameAllocator = abiFunction.newNameAllocator(),
        )
        context(codeBuilder) {
          val importFunction = WasmImportFunction(abiWorld, abiFunction)
          val returnValue = importFunction.call(
            receiver = null,
            parameters = abiFunction.parameters.liftedSpecs.map { it.valueExpression },
          )
          val resultValue = abiFunction.result.value
          if (resultValue != null) {
            returns(resultValue.type)
            codeBuilder.add("return %L", returnValue.code)
          }
          addCode(codeBuilder.build())
        }
      }
      .build()
  }

  private fun byteCountProperty(): PropertySpec {
    return PropertySpec.builder("byteCount", INT)
      .addModifiers(KModifier.OVERRIDE)
      .getter(
        FunSpec.getterBuilder()
          .addStatement("return %L", abiStream.elementEncoder?.byteCount ?: 0)
          .build(),
      )
      .build()
  }

  private fun loadFunction(): FunSpec {
    val codeBuilder = CodeBuilder(
      bridge = bridge,
      platform = platform,
      nameAllocator = NameAllocator(),
    )

    val addressName = codeBuilder.newName("address")
    val encoderContext = RealEncoderContext(
      collector = encoderFunctionCollector,
      streamBindingsCollector = streamBindingsCollector,
      orientation = Orientation.Import, // TODO: orientation of stream-declaring function?
    )

    return FunSpec.builder("load")
      .addModifiers(KModifier.OVERRIDE)
      .addParameter(addressName, platform.addressType)
      .returns(elementTypeKt)
      .apply {
        context(codeBuilder) {
          if (abiStream.elementEncoder != null) {
            val result = encoderContext.load(
              encoder = abiStream.elementEncoder,
              baseAddress = CodeBlock.of("%N", addressName),
              offset = 0,
            )
            codeBuilder.addStatement("return %L", result)
          }
          addCode(codeBuilder.build())
        }
      }
      .build()
  }

  private fun storeFunction(): FunSpec {
    val codeBuilder = CodeBuilder(
      bridge = bridge,
      platform = platform,
      nameAllocator = NameAllocator(),
    )

    val memoryAllocatorName = codeBuilder.newName("memoryAllocator")
    val addressName = codeBuilder.newName("address")
    val valueName = codeBuilder.newName("value")
    val memoryAllocator = platform.getMemoryAllocator(memoryAllocatorName)

    val encoderContext = RealEncoderContext(
      collector = encoderFunctionCollector,
      streamBindingsCollector = streamBindingsCollector,
      orientation = Orientation.Import, // TODO: orientation of stream-declaring function?
    )

    return FunSpec.builder("store")
      .addModifiers(KModifier.OVERRIDE)
      .addParameter(memoryAllocator.name, memoryAllocator.type)
      .addParameter(addressName, platform.addressType)
      .addParameter(valueName, elementTypeKt)
      .apply {
        context(codeBuilder) {
          if (abiStream.elementEncoder != null) {
            encoderContext.store(
              encoder = abiStream.elementEncoder,
              memoryAllocator = memoryAllocator,
              baseAddress = CodeBlock.of("%N", addressName),
              offset = 0,
              value = CodeBlock.of("%N", valueName),
            )
          }
          addCode(codeBuilder.build())
        }
      }
      .build()
  }
}

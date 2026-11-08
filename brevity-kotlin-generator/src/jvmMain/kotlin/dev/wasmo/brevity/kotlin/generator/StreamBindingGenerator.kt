package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.abi.AbiFunction
import dev.wasmo.brevity.kotlin.abi.AbiStream
import dev.wasmo.brevity.kotlin.abi.AbiWorld
import dev.wasmo.brevity.kotlin.abi.WasmImportFunction
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.expressions.valueExpression

internal class StreamBindingGenerator(
  private val platform: Platform,
  kotlinMapper: KotlinMapper,
  typeNamer: TypeNamer,
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
      .apply {
        for (abiFunction in abiStream.functions) {
          addFunction(guestFunction(abiFunction))
        }
      }
      .addCommonBindings()
      .build()
  }

  fun generateHost(): TypeSpec {
    return TypeSpec.classBuilder(className)
      .superclass(bindingSuperclass)
      .addCommonBindings()
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
          bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
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

  private fun TypeSpec.Builder.addCommonBindings() = apply {
    addProperty(
      PropertySpec.builder("byteCount", INT)
        .addModifiers(KModifier.OVERRIDE)
        .getter(FunSpec.getterBuilder()
          .addStatement("return %L", abiStream.elementEncoder?.byteCount ?: 0)
          .build())
        .build()
    )
    addFunction(
      FunSpec.builder("lower")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("address", INT)
        .addParameter("value", elementTypeKt)
        .build(),
    )
    addFunction(
      FunSpec.builder("lift")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter("address", INT)
        .returns(elementTypeKt)
        .addStatement("TODO(%S)", "implement this!")
        .build(),
    )
  }
}

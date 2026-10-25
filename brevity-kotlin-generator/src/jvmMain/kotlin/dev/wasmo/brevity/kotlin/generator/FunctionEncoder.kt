package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.kotlin.abi.AbiFunction
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Parameters
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result
import dev.wasmo.brevity.kotlin.abi.AbiInterface
import dev.wasmo.brevity.kotlin.abi.AbiResource
import dev.wasmo.brevity.kotlin.abi.AbiService
import dev.wasmo.brevity.kotlin.abi.AbiWorld
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.code.withMemoryAllocator
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.makeImmediate

/**
 * Lift and lower receivers, parameters, and results.
 */
class FunctionEncoder(
  val collector: EncoderFunctionCollector,
  val service: AbiService,
  val function: AbiFunction,
) {
  val resourceImplementation: ClassName?
    get() = when {
      service is AbiResource -> collector.typeNamer.implementation(service.type)

      function.name is FunctionName.ResourceNew ->
        collector.typeNamer.implementation(function.name.resourceType)

      else -> null
    }

  context(codeBuilder: CodeBuilder)
  fun liftParameters(
    p: Iterator<KtExpression>,
  ): List<KtExpression> {
    val encoderContext = RealEncoderContext(collector, function)
    val parameters = function.parameters
    val values = when (parameters) {
      is Parameters.Flattened -> parameters.parameters.map { flatParameter ->
        encoderContext.liftFlat(
          encoder = flatParameter.encoder,
          values = flatParameter.encoder.coreTypes.map { p.next().code },
        )
      }

      is Parameters.Stored -> context(encoderContext) {
        parameters.loadAll(
          baseAddress = codeBuilder.platform.liftAddress(p.next().code),
        )
      }
    }

    check(parameters.parameters.size == values.size)

    return parameters.parameters.zip(values).map { (flatParameter, value) ->
      CodeBlockExpression(
        type = flatParameter.type,
        nameHint = flatParameter.name,
        code = value,
      )
    }
  }

  context(codeBuilder: CodeBuilder)
  fun lowerParameters(
    parameterValues: List<KtExpression>,
    memoryAllocator: MemoryAllocator?,
  ): List<KtExpression> {
    val encoderContext = RealEncoderContext(collector, function)
    return buildList {
      when (val parameters = function.parameters) {
        is Parameters.Flattened -> {
          for ((p, coreParameter) in parameters.parameters.withIndex()) {
            val coreValues = encoderContext.lowerFlat(
              coreParameter.encoder,
              parameterValues[p].code,
              memoryAllocator,
            )
            for ((index, loweredSpec) in coreParameter.loweredSpecs.withIndex()) {
              add(
                CodeBlockExpression(
                  type = loweredSpec.type,
                  nameHint = loweredSpec.name,
                  code = coreValues[index],
                ),
              )
            }
          }
        }

        is Parameters.Stored -> {
          codeBuilder.addStatement(
            "val %N = %L",
            parameters.addressSpec.name,
            memoryAllocator!!.allocate("%L", parameters.byteCount),
          )
          val addressParameterValue = CodeBlock.of(
            "%N",
            parameters.addressSpec.name,
          )
          context(encoderContext) {
            parameters.storeAll(
              memoryAllocator = memoryAllocator,
              baseAddress = addressParameterValue,
              fieldValues = parameterValues.map { it.code },
            )
          }
          add(
            CodeBlockExpression(
              type = parameters.addressSpec.type,
              nameHint = parameters.addressSpec.name,
              code = codeBuilder.platform.lowerAddress(addressParameterValue),
            ),
          )
        }
      }

      val result = function.result
      if (result is Result.PointerParameter) {
        codeBuilder.addStatement(
          "val %N = %L",
          result.parameter.name,
          memoryAllocator!!.allocate("%L", result.value.encoder.byteCount),
        )
        val pointer = CodeBlock.of("%N", result.parameter.name)
        add(
          CodeBlockExpression(
            type = result.parameter.type,
            nameHint = result.parameter.name,
            code = codeBuilder.platform.lowerAddress(pointer),
          ),
        )
      }
    }
  }

  context(codeBuilder: CodeBuilder)
  fun liftPointerParameter(
    p: Iterator<KtExpression>,
  ): CodeBlock? {
    val result = function.result
    if (result !is Result.PointerParameter) return null

    codeBuilder.addStatement(
      "val %N = %L",
      result.parameter.name,
      codeBuilder.platform.liftAddress(p.next().code),
    )
    return CodeBlock.of("%N", result.parameter.name)
  }

  context(codeBuilder: CodeBuilder)
  fun liftReceiver(
    iterator: Iterator<Pair<AbiFunction.FlatParameter, KtExpression>>,
  ): KtExpression {
    val encoderContext = RealEncoderContext(collector, function)
    val receiverValue = when (service) {
      is AbiWorld -> codeBuilder.platform.getParent(service)
      is AbiInterface -> codeBuilder.platform.getParent(service)
      is AbiResource -> {
        val (spec, value) = iterator.next()
        check(spec is AbiFunction.FlatParameter.ResourceRep)
        CodeBlockExpression(
          type = service.apiClassName,
          nameHint = "receiver",
          code = context(encoderContext) {
            codeBuilder.platform.liftResource(
              id = value.code,
              resourceApiName = service.apiClassName,
              resourceImplementationName = resourceImplementation!!,
              orientation = function.orientation,
            )
          },
        )
      }
    }

    return receiverValue.makeImmediate()
  }

  context(codeBuilder: CodeBuilder)
  fun liftReturnValue(callResult: KtExpression): CodeBlock {
    val encoderContext = RealEncoderContext(collector, function)
    return when (val result = function.result) {
      Result.Void -> KtExpression.Unit.code
      is Result.SingleCoreValueReturn -> encoderContext.liftFlat(
        result.value.encoder,
        listOf(callResult.code),
      )

      is Result.PointerReturn -> encoderContext.load(
        result.value.encoder,
        codeBuilder.platform.liftAddress(callResult.code),
      )

      is Result.PointerParameter -> encoderContext.load(
        result.value.encoder,
        CodeBlock.of("%N", result.parameter.name),
      )

      is Result.CallTaskReturn -> CodeBlock.of(
        "%L.awaitTaskResult<%T>()",
        codeBuilder.bridge,
        result.value?.type ?: UNIT,
      )
    }
  }

  context(codeBuilder: CodeBuilder)
  fun lowerResult(
    resultValue: KtExpression,
    pointerParameterValue: CodeBlock?,
  ): KtExpression {
    val encoderContext = RealEncoderContext(collector, function)
    return when (val result = function.result) {
      Result.Void -> KtExpression.Unit

      is Result.SingleCoreValueReturn -> {
        withMemoryAllocator(
          lowerAllocates = result.value.encoder.lowerAllocates,
        ) { memoryAllocator ->
          CodeBlockExpression(
            type = result.value.encoder.coreTypes.single().kotlinCoreType,
            nameHint = "result",
            code = encoderContext.lowerFlat(
              encoder = result.value.encoder,
              memoryAllocator = memoryAllocator,
              value = resultValue.code,
            ).single(),
          )
        }
      }

      is Result.PointerReturn -> {
        withMemoryAllocator(
          lowerAllocates = true,
        ) { memoryAllocator ->
          val codeBuilder = contextOf<CodeBuilder>()
          val addressName = codeBuilder.newName("address")
          codeBuilder.addStatement(
            "val %N = %L",
            addressName,
            memoryAllocator!!.allocate("%L", result.value.encoder.byteCount),
          )
          val resultAddressValue = CodeBlock.of("%N", addressName)
          encoderContext.store(
            encoder = result.value.encoder,
            memoryAllocator = memoryAllocator,
            baseAddress = resultAddressValue,
            value = resultValue.code,
          )
          CodeBlockExpression(
            type = CoreType.Pointer.kotlinCoreType,
            nameHint = "resultAddress",
            code = codeBuilder.platform.lowerAddress(resultAddressValue),
          )
        }
      }

      is Result.PointerParameter -> {
        withMemoryAllocator(
          lowerAllocates = result.value.encoder.lowerAllocates,
        ) { memoryAllocator ->
          encoderContext.store(
            encoder = result.value.encoder,
            memoryAllocator = memoryAllocator,
            baseAddress = pointerParameterValue!!,
            value = resultValue.code,
          )
          KtExpression.Unit
        }
      }

      is Result.CallTaskReturn -> error("unexpected call")
    }
  }
}

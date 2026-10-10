package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.code.withMemoryAllocator
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Parameters.Flattened
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Parameters.Stored
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Result.PointerParameter
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Result.PointerReturn
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Result.SingleCoreValueReturn
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Result.Void
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

class AbiLower(
  private val abiFunction: AbiFunction,
  private val loweredFunction: KtFunction,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression = withMemoryAllocator(
    lowerAllocates = abiFunction.parameters.lowerAllocates
      || abiFunction.result is PointerParameter,
  ) { memoryAllocator ->
    val loweredParameterValues = abiFunction.lowerParameterValues(parameters, memoryAllocator)
    val loweredResult = loweredFunction.call(receiver, loweredParameterValues)
    val liftedResult = abiFunction.liftReturnValue(loweredResult)
    CodeBlockExpression(
      type = abiFunction.result.value?.type ?: UNIT,
      nameHint = "result",
      code = liftedResult,
    )
  }
}

context(codeBuilder: CodeBuilder)
private fun AbiFunction.lowerParameterValues(
  parameterValues: List<KtExpression>,
  memoryAllocator: MemoryAllocator?,
) = buildList {
  if (isResource) {
    add(
      CodeBlockExpression(
        type = CoreType.I32.kotlinCoreType,
        nameHint = "id",
        code = when {
          // TODO: this is an unfortunate hack.
          //  We should wire the name of the enclosing 'self' through.
          name is FunctionName.TaskReturn -> CodeBlock.of("%N", "self")
          else -> CodeBlock.of("this.%N", "id")
        },
      ),
    )
  }

  when (parameters) {
    is Flattened -> {
      for ((p, coreParameter) in parameters.parameters.withIndex()) {
        val coreValues = coreParameter.encoder.lowerFlat(
          memoryAllocator,
          parameterValues[p].code,
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

    is Stored -> {
      codeBuilder.addStatement(
        "val %N = %L",
        parameters.addressSpec.name,
        memoryAllocator!!.allocate("%L", parameters.byteCount),
      )
      val addressParameterValue = CodeBlock.of(
        "%N",
        parameters.addressSpec.name,
      )
      parameters.storeAll(
        memoryAllocator = memoryAllocator,
        baseAddress = addressParameterValue,
        fieldValues = parameterValues.map { it.code },
      )
      add(
        CodeBlockExpression(
          type = parameters.addressSpec.type,
          nameHint = parameters.addressSpec.name,
          code = codeBuilder.platform.lowerAddress(addressParameterValue),
        ),
      )
    }
  }

  if (result is PointerParameter) {
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

context(codeBuilder: CodeBuilder)
private fun AbiFunction.liftReturnValue(callResult: KtExpression): CodeBlock {
  return when (result) {
    Void -> KtExpression.Unit.code
    is SingleCoreValueReturn -> result.value.encoder.liftFlat(listOf(callResult.code))
    is PointerReturn -> result.value.encoder.load(
      codeBuilder.platform.liftAddress(callResult.code)
    )
    is PointerParameter -> result.value.encoder.load(
      CodeBlock.of("%N", result.parameter.name)
    )
  }
}

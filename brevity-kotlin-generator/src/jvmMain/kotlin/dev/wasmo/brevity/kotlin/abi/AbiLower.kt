package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Parameters.Flattened
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Parameters.Stored
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.PointerParameter
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.PointerReturn
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.SingleCoreValueReturn
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.Void
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.MemoryAllocator
import dev.wasmo.brevity.kotlin.code.withMemoryAllocator
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction

context(codeBuilder: CodeBuilder)
fun AbiLower(
  abiService: AbiService,
  abiFunction: AbiFunction,
  resourceId: KtExpression?,
): KtFunction = AbiLower(
  abiFunction = abiFunction,
  loweredFunction = codeBuilder.platform.createLowered(abiService, abiFunction),
  resourceId = resourceId,
)

private class AbiLower(
  private val abiFunction: AbiFunction,
  private val loweredFunction: KtFunction,
  private val resourceId: KtExpression?,
) : KtFunction {
  init {
    check(abiFunction.isResource == (resourceId != null))
  }

  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression = withMemoryAllocator(
    lowerAllocates = abiFunction.parameters.lowerAllocates
      || abiFunction.result is PointerParameter,
  ) { memoryAllocator ->
    val loweredParameterValues = abiFunction.lowerParameterValues(
      resourceId = resourceId,
      parameterValues = parameters,
      memoryAllocator = memoryAllocator,
    )
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
  resourceId: KtExpression?,
  parameterValues: List<KtExpression>,
  memoryAllocator: MemoryAllocator?,
) = buildList {
  if (isResource) {
    add(resourceId!!)
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
      codeBuilder.platform.liftAddress(callResult.code),
    )

    is PointerParameter -> result.value.encoder.load(
      CodeBlock.of("%N", result.parameter.name),
    )

    is AbiFunction.Result.CallTaskReturn -> CodeBlock.of(
      "%L.awaitTaskResult<%T>()",
      codeBuilder.bridge,
      result.value?.type ?: UNIT,
    )
  }
}

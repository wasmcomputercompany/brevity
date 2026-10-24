package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.PointerParameter
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.withMemoryAllocator
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction
import dev.wasmo.brevity.kotlin.generator.FunctionEncoder

context(codeBuilder: CodeBuilder)
fun AbiLower(
  resourceId: KtExpression?,
  functionEncoder: FunctionEncoder,
): KtFunction = AbiLower(
  loweredFunction = codeBuilder.platform.createLowered(
    functionEncoder.service,
    functionEncoder.function,
  ),
  resourceId = resourceId,
  functionEncoder = functionEncoder,
)

private class AbiLower(
  private val loweredFunction: KtFunction,
  private val resourceId: KtExpression?,
  private val functionEncoder: FunctionEncoder,
) : KtFunction {
  private val abiFunction: AbiFunction
    get() = functionEncoder.function

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
    val loweredParameterValues = functionEncoder.lowerParameters(
      parameterValues = buildList {
        if (resourceId != null) add(resourceId)
        addAll(parameters)
      },
      memoryAllocator = memoryAllocator,
    )
    val loweredResult = loweredFunction.call(receiver, loweredParameterValues)
    val liftedResult = functionEncoder.liftReturnValue(loweredResult)
    CodeBlockExpression(
      type = abiFunction.result.value?.type ?: UNIT,
      nameHint = "result",
      code = liftedResult,
    )
  }
}

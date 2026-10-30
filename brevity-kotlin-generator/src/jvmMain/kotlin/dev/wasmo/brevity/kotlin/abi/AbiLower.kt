package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.PointerParameter
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.withMemoryAllocator
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction
import dev.wasmo.brevity.kotlin.generator.FunctionEncoder
import dev.wasmo.brevity.kotlin.generator.Symbols

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
  init {
    check(functionEncoder.function.isResource == (resourceId != null))
  }

  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    val abiFunction = functionEncoder.function
    return withMemoryAllocator(
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

      if (functionEncoder.function.isTask) {
        return@withMemoryAllocator callAwaitTaskComplete(loweredResult, receiver)
      }

      if (functionEncoder.function.isSubtask) {
        contextOf<CodeBuilder>().addStatement(
          "%L.awaitSubtaskComplete(%T(%L.toUInt()))",
          codeBuilder.bridge,
          Symbols.Brevity.PackedSubtaskResult,
          loweredResult.code,
        )
      }

      CodeBlockExpression(
        type = abiFunction.result.value?.type ?: UNIT,
        nameHint = "result",
        code = functionEncoder.liftReturnValue(loweredResult),
      )
    }
  }

  context(codeBuilder: CodeBuilder)
  private fun callAwaitTaskComplete(
    packedAsyncResult: KtExpression,
    receiver: KtExpression?,
  ): KtExpression {
    val resultValue = functionEncoder.function.result.value
    val resultName = resultValue?.let {
      codeBuilder.newName("result")
        .also { codeBuilder.add("val %N = ", it) }
    }

    val eventName = codeBuilder.newName("event")
    codeBuilder.controlFlow(
      "%L.awaitTaskComplete<%T>(%T(%L.toUInt())) { %N ->",
      codeBuilder.bridge,
      resultValue?.type ?: UNIT,
      Symbols.Brevity.PackedAsyncResult,
      packedAsyncResult.code,
      eventName,
    ) {
      val callback = codeBuilder.platform.createLowered(
        functionEncoder.service,
        functionEncoder.function.asyncExportCallback!!,
      )

      val callbackResult = callback.call(
        receiver = receiver,
        parameters = listOf(
          CodeBlockExpression(INT, "eventCode", CodeBlock.of("%N.code", eventName), immediate = true),
          CodeBlockExpression(INT, "p1", CodeBlock.of("%N.p1", eventName), immediate = true),
          CodeBlockExpression(INT, "p2", CodeBlock.of("%N.p2", eventName), immediate = true),
        ),
      )
      contextOf<CodeBuilder>().addStatement(
        "return@awaitTaskComplete %T(%L.toUInt())",
        Symbols.Brevity.PackedAsyncResult,
        callbackResult.code,
      )
    }

    if (resultName == null) return KtExpression.Unit

    return CodeBlockExpression(
      type = resultValue.type,
      nameHint = "result",
      code = CodeBlock.of("%N", resultName),
    )
  }
}

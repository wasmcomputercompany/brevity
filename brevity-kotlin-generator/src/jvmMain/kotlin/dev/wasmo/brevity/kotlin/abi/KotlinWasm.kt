package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.MemberName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction
import dev.wasmo.brevity.kotlin.generator.importFunctionName

class WasmImportFunction(
  private val abiService: AbiService,
  private val abiFunction: AbiFunction,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)
    check(parameters.size == abiFunction.loweredParameterTypes.size)

    val resultValue = abiFunction.result.value
    val (resultName, resultType) = when {
      abiFunction.isSubtask -> codeBuilder.newName("packedSubtaskResult") to INT
      abiFunction.result is AbiFunction.Result.PointerParameter -> null to null
      resultValue != null -> codeBuilder.newName("result") to resultValue.type
      else -> null to null
    }

    if (resultName != null) {
      codeBuilder.add("val %N = ", resultName)
    }

    codeBuilder.add(
      "%M(⇥",
      MemberName(abiService.apiClassName.packageName, abiFunction.name.importFunctionName),
    )
    if (parameters.isNotEmpty()) {
      codeBuilder.add("\n")
    }
    for (parameter in parameters) {
      codeBuilder.add("%L,\n", parameter.code)
    }
    codeBuilder.add("⇤)\n")

    if (resultName == null || resultType == null) return KtExpression.Unit

    return CodeBlockExpression(
      type = resultType,
      nameHint = resultName,
      code = CodeBlock.of("%N", resultName),
      immediate = true,
    )
  }
}

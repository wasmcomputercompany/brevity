package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.generator.importFunctionName

class WasmImportFunction(
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
    val resultName = when {
      resultValue != null -> codeBuilder.newName("result")
      else -> null
    }

    if (resultName != null) {
      codeBuilder.add("val %N = ", resultName)
    }

    codeBuilder.add("%N(⇥", abiFunction.name.importFunctionName)
    if (parameters.isNotEmpty()) {
      codeBuilder.add("\n")
    }
    for (parameter in parameters) {
      codeBuilder.add("%L,\n", parameter.code)
    }
    codeBuilder.add("⇤)\n")

    if (resultName == null || resultValue == null) return KtExpression.Unit

    return CodeBlockExpression(
      type = resultValue.type,
      nameHint = resultName,
      code = CodeBlock.of("%N", resultName),
      immediate = true,
    )
  }
}

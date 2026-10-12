package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction

class CallLifted(
  val abiFunction: AbiFunction,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver != null)
    check(parameters.size == abiFunction.parameters.liftedSpecs.size)

    val resultValue = abiFunction.result.value
    val resultName = when {
      resultValue != null -> codeBuilder.newName("result")
      else -> null
    }

    if (resultName != null) {
      codeBuilder.add("val %N = ", resultName)
    }

    codeBuilder.add(
      "%L.%N(⇥",
      receiver.code,
      abiFunction.kotlinName,
    )
    if (parameters.isNotEmpty()) {
      codeBuilder.add("\n")
    }
    for ((spec, value) in abiFunction.parameters.liftedSpecs.zip(parameters)) {
      codeBuilder.add(
        "%N = %L,\n",
        spec.name,
        value.code,
      )
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

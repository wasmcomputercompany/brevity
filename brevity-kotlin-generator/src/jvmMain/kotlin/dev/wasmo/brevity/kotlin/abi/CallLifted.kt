package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction
import dev.wasmo.brevity.kotlin.expressions.makeImmediate
import dev.wasmo.brevity.kotlin.generator.FunctionEncoder

class CallLifted(
  private val functionEncoder: FunctionEncoder,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val abiFunction = functionEncoder.function
    val resultValue = abiFunction.result.value
    val resultName = when {
      resultValue != null -> codeBuilder.newName("result")
      else -> null
    }

    check(parameters.size == abiFunction.parameters.parameters.size)
    val p = abiFunction.parameters.parameters.zip(parameters).iterator()
    val receiver = functionEncoder.liftReceiver(p).makeImmediate()
    if (resultName != null) {
      codeBuilder.add("val %N = ", resultName)
    }
    codeBuilder.add(
      "%L.%N(⇥",
      receiver.code,
      abiFunction.kotlinName,
    )

    if (p.hasNext()) {
      codeBuilder.add("\n")
    }
    while (p.hasNext()) {
      val (spec, value) = p.next()
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

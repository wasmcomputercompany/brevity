package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.encoders.liftResource
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction
import dev.wasmo.brevity.kotlin.expressions.makeImmediate

class CallLifted(
  val parentService: AbiService,
  val abiFunction: AbiFunction,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val resultValue = abiFunction.result.value
    val resultName = when {
      resultValue != null -> codeBuilder.newName("result")
      else -> null
    }

    check(parameters.size == abiFunction.parameters.parameters.size)
    val p = abiFunction.parameters.parameters.zip(parameters).iterator()
    val receiver = liftReceiver(p).makeImmediate()
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

  context(codeBuilder: CodeBuilder)
  private fun liftReceiver(
    iterator: Iterator<Pair<AbiFunction.FlatParameter, KtExpression>>,
  ): KtExpression {
    val receiverValue = when (parentService) {
      is AbiWorld -> codeBuilder.platform.getParent(parentService)
      is AbiInterface -> codeBuilder.platform.getParent(parentService)
      is AbiResource -> {
        val (spec, value) = iterator.next()
        check(spec is AbiFunction.FlatParameter.ResourceRep)
        CodeBlockExpression(
          type = parentService.apiClassName,
          nameHint = "receiver",
          code = liftResource(
            id = value.code,
            abiClassName = parentService.apiClassName,
          ),
        )
      }
    }

    return receiverValue.makeImmediate()
  }
}

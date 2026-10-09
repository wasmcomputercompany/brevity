package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.TypeName
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.generator.exportFunctionName
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

/**
 * A function that accepts a [LongArray], lifts it, calls [abiFunction], lowers the result to
 * another [LongArray].
 */
class ChicoryImportFunction(
  private val abiFunction: AbiFunction,
  private val abiLift: AbiLift,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)
    check(parameters.single().type == LONG_ARRAY)

    val array = parameters.single().makeImmediate()

    val coreValues = abiFunction.loweredParameterTypes.withIndex().map { (index, coreType) ->
      CodeBlockExpression(
        type = coreType.kotlinCoreType,
        nameHint = "p$index",
        code = CodeBlock.of("%L[%L]", array.code, index).longToCoreValue(coreType.kotlinCoreType),
        immediate = true,
      )
    }

    val resultValue = abiLift.call(null, coreValues).makeImmediate()

    if (abiFunction.result.loweredType == null) {
      return CodeBlockExpression(
        type = LONG_ARRAY,
        nameHint = "resultArray",
        code = CodeBlock.of("longArrayOf()"),
        immediate = true,
      )
    }

    return CodeBlockExpression(
      type = LONG_ARRAY,
      nameHint = "resultArray",
      code = CodeBlock.of("longArrayOf(%L)", resultValue.coreValueToLong()),
      immediate = true,
    )
  }
}

/**
 * Invokes a [ChicoryExportFunction] by combining input core values into a [LongArray] and
 * decomposing the result [LongArray] into a core value.
 */
class ChicoryExportFunction(
  private val abiFunction: AbiFunction,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val arrayName = codeBuilder.newName("resultArray")
    if (abiFunction.result != AbiFunction.Result.Void && abiFunction.name !is FunctionName.AsyncLift) {
      codeBuilder.add("val %N = ", arrayName)
    }

    codeBuilder.add("%N.apply(⇥\n", abiFunction.name.exportFunctionName)
    for (parameterValue in parameters) {
      codeBuilder.add("%L,\n", parameterValue.coreValueToLong())
    }
    codeBuilder.add("⇤)\n")

    val resultValue = abiFunction.result.value
    val resultLoweredType = abiFunction.result.loweredType

    if (resultValue == null
      || abiFunction.name is FunctionName.AsyncLift
      || resultLoweredType == null
    ) {
      return KtExpression.Unit
    }

    return CodeBlockExpression(
      type = resultValue.type,
      nameHint = "result",
      code = CodeBlock.of("%N[%L]", arrayName, 0).longToCoreValue(resultLoweredType.kotlinCoreType),
      immediate = true,
    )
  }
}

/** Everything in Chicory is a [Long], so we need to convert core types. */
internal fun KtExpression.coreValueToLong(): CodeBlock = when (type) {
  FLOAT -> CodeBlock.of("%L.toBits().toLong()", code)
  DOUBLE -> CodeBlock.of("%L.toBits()", code)
  INT -> CodeBlock.of("%L.toLong()", code)
  LONG -> code
  else -> error("unexpected type: $type")
}

/** Everything in Chicory is a [Long], so we need to convert core types. */
internal fun CodeBlock.longToCoreValue(type: TypeName): CodeBlock = when (type) {
  FLOAT -> CodeBlock.of("%T.fromBits(%L.toInt())", FLOAT, this)
  DOUBLE -> CodeBlock.of("%T.fromBits(%L)", DOUBLE, this)
  INT -> CodeBlock.of("%L.toInt()", this)
  LONG -> this
  else -> error("unexpected type: $type")
}

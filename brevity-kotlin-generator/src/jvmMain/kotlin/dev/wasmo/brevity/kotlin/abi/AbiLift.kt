package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.INT
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction
import dev.wasmo.brevity.kotlin.expressions.makeImmediate
import dev.wasmo.brevity.kotlin.generator.FunctionEncoder
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

context(codeBuilder: CodeBuilder)
fun AbiLift(
  functionEncoder: FunctionEncoder,
): KtFunction {
  return when (functionEncoder.function.name) {
    is FunctionName.AsyncExport -> AbiLiftAsyncExport(
      functionEncoder = functionEncoder,
      liftedFunction = CallLifted(functionEncoder),
    )

    is FunctionName.AsyncImport -> AbiLiftAsyncImport(
      functionEncoder = functionEncoder,
      liftedFunction = CallLifted(functionEncoder),
    )

    is FunctionName.ResourceNew -> AbiResourceNewLift(
      functionEncoder = functionEncoder,
    )

    is FunctionName.ResourceDrop -> AbiResourceDropLift(
      functionEncoder = functionEncoder,
    )

    is FunctionName.AsyncExportCallback -> AbiAsyncExportCallbackLift()

    is FunctionName.TaskReturn -> AbiTaskReturnLift(
      functionEncoder = functionEncoder,
    )

    else -> AbiSyncLift(
      functionEncoder = functionEncoder,
      liftedFunction = CallLifted(functionEncoder),
    )
  }
}

private class AbiSyncLift(
  private val functionEncoder: FunctionEncoder,
  private val liftedFunction: KtFunction,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val p = parameters.iterator()
    val liftedParametersValues = functionEncoder.liftParameters(p).map { it.makeImmediate() }
    val liftedPointerParameterValue = functionEncoder.liftPointerParameter(p)?.makeImmediate()
    check(!p.hasNext()) { "unexpected parameter count" }
    codeBuilder.platform.afterLiftParameters()

    val resultValue = liftedFunction.call(
      null,
      liftedParametersValues,
    ).makeImmediate()

    return functionEncoder.lowerResult(resultValue, liftedPointerParameterValue?.code)
  }
}

private class AbiTaskReturnLift(
  private val functionEncoder: FunctionEncoder,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val p = parameters.iterator()
    val liftedParameterValues = functionEncoder.liftParameters(p).map { it.makeImmediate() }
    check(!p.hasNext()) { "unexpected parameter count" }
    codeBuilder.platform.afterLiftParameters()

    codeBuilder.add("%L.currentThread.taskReturn(⇥", codeBuilder.bridge)
    if (functionEncoder.function.parameters.parameters.isNotEmpty()) {
      codeBuilder.add("\n%L,\n", liftedParameterValues.single().code)
    }
    codeBuilder.add("⇤)\n")
    return KtExpression.Unit
  }
}

private class AbiResourceNewLift(
  private val functionEncoder: FunctionEncoder,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    codeBuilder.platform.afterLiftParameters()
    val handleName = codeBuilder.newName("handle")
    val indexName = codeBuilder.newName("index")
    codeBuilder.addStatement("val %N = %T()", handleName, functionEncoder.resourceImplementation)
    codeBuilder.addStatement("%N.%N = %L", handleName, "rep", parameters.single().code)
    codeBuilder.addStatement(
      "val %N = %L.putExportedResource(%N)",
      indexName,
      codeBuilder.bridge,
      handleName,
    )
    return CodeBlockExpression(
      INT,
      "index",
      CodeBlock.of(
        "%N",
        indexName,
      ),
    )
  }
}

private class AbiResourceDropLift(
  private val functionEncoder: FunctionEncoder,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)
    codeBuilder.platform.afterLiftParameters()
    codeBuilder.addStatement("// TODO: AbiResourceDropLift")
    return KtExpression.Unit
  }
}

private class AbiAsyncExportCallbackLift : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    val packedAsyncResultName = codeBuilder.newName("packedAsyncResult")
    codeBuilder.addStatement(
      "val %N = %L.resumeTask(%T(%L, %L, %L))",
      packedAsyncResultName,
      codeBuilder.bridge,
      Symbols.Brevity.Event,
      parameters[0].code,
      parameters[1].code,
      parameters[2].code,
    )
    return CodeBlockExpression(
      type = INT,
      nameHint = "packedAsyncResultName",
      code = CodeBlock.of("%L.value.toInt()", packedAsyncResultName),
      immediate = true,
    )
  }
}

/** The function returns a `PackedAsyncResult`. */
private class AbiLiftAsyncExport(
  private val functionEncoder: FunctionEncoder,
  private val liftedFunction: KtFunction,
) : KtFunction {
  private val result = functionEncoder.function.result as AbiFunction.Result.CallTaskReturn

  init {
    require(functionEncoder.function.name is FunctionName.AsyncExport)
  }

  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val p = parameters.iterator()
    val liftedParametersValues = functionEncoder.liftParameters(p).map { it.makeImmediate() }
    check(!p.hasNext()) { "unexpected parameter count" }
    codeBuilder.platform.afterLiftParameters()

    val packedAsyncResultName = codeBuilder.newName("packedAsyncResult")
    codeBuilder.controlFlow(
      "val %N = %L.launchTask",
      packedAsyncResultName,
      codeBuilder.bridge,
    ) {
      val resultValue = liftedFunction.call(
        null,
        liftedParametersValues,
      ).makeImmediate()

      val taskReturn = AbiLower(
        resourceId = null,
        functionEncoder = FunctionEncoder(
          collector = functionEncoder.collector,
          service = functionEncoder.service,
          function = result.taskReturn,
        ),
      )
      taskReturn.call(
        receiver = null,
        parameters = buildList {
          if (result.value != null) {
            add(resultValue)
          }
        },
      )
    }

    return CodeBlockExpression(
      type = CoreType.I32.kotlinCoreType,
      nameHint = "packedAsyncResult",
      code = CodeBlock.of("%N.value.toInt()", packedAsyncResultName),
      immediate = true,
    )
  }
}

/** The function returns a `PackedSubtaskResult`. */
private class AbiLiftAsyncImport(
  private val functionEncoder: FunctionEncoder,
  private val liftedFunction: KtFunction,
) : KtFunction {
  init {
    require(functionEncoder.function.name is FunctionName.AsyncImport)
  }

  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val p = parameters.iterator()
    val liftedParametersValues = functionEncoder.liftParameters(p).map { it.makeImmediate() }
    val liftedPointerParameterValue = functionEncoder.liftPointerParameter(p)?.makeImmediate()
    check(!p.hasNext()) { "unexpected parameter count" }
    codeBuilder.platform.afterLiftParameters()

    val packedSubtaskResultName = codeBuilder.newName("packedSubtaskResult")
    codeBuilder.controlFlow(
      "val %N = %L.launchSubtask",
      packedSubtaskResultName,
      codeBuilder.bridge,
    ) {
      val resultValue = liftedFunction.call(
        null,
        liftedParametersValues,
      ).makeImmediate()

      functionEncoder.lowerResult(resultValue, liftedPointerParameterValue?.code)
    }

    return CodeBlockExpression(
      type = CoreType.I32.kotlinCoreType,
      nameHint = "packedSubtaskResult",
      code = CodeBlock.of("%N.value.toInt()", packedSubtaskResultName),
      immediate = true,
    )
  }
}

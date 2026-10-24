package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.ClassName
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
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

context(codeBuilder: CodeBuilder)
fun AbiLift(
  functionEncoder: FunctionEncoder,
  resourceImplementation: ClassName? = null,
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
      resourceImplementation = resourceImplementation!!,
    )

    is FunctionName.ResourceDrop -> AbiResourceDropLift(
      functionEncoder = functionEncoder,
    )

    is FunctionName.AsyncExportCallback -> object : KtFunction {
      context(codeBuilder: CodeBuilder)
      override fun call(
        receiver: KtExpression?,
        parameters: List<KtExpression>,
      ): KtExpression {
        codeBuilder.addStatement("TODO(%S)", "implement AbiLift for AsyncLiftCallbacks")
        return KtExpression.Unit
      }
    }

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
    val liftedParametersValues = functionEncoder.liftParameters(p)
    val liftedPointerParameterValue = functionEncoder.liftPointerParameter(p)

    check(!p.hasNext()) {
      "unexpected parameter count"
    }

    codeBuilder.platform.afterLiftParameters()

    val resultValue = liftedFunction.call(
      null,
      liftedParametersValues,
    ).makeImmediate()

    return functionEncoder.lowerResult(resultValue, liftedPointerParameterValue)
  }
}

private class AbiTaskReturnLift(
  private val functionEncoder: FunctionEncoder,
) : KtFunction {
  private val abiFunction: AbiFunction
    get() = functionEncoder.function

  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val p = parameters.iterator()
    val liftedParameterValues = functionEncoder.liftParameters(p)
    check(!p.hasNext())

    codeBuilder.platform.afterLiftParameters()

    codeBuilder.add("%L.taskReturn(⇥", codeBuilder.bridge)
    if (abiFunction.parameters.parameters.isNotEmpty()) {
      codeBuilder.add("\n%L,\n", liftedParameterValues.single().code)
    }
    codeBuilder.add("⇤)\n")
    return KtExpression.Unit
  }
}

private class AbiResourceNewLift(
  private val functionEncoder: FunctionEncoder,
  private val resourceImplementation: ClassName,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)
    codeBuilder.platform.afterLiftParameters()
    return CodeBlockExpression(
      INT,
      "index",
      CodeBlock.of(
        "%L.putResource(%T(%N = %L))",
        codeBuilder.bridge,
        resourceImplementation,
        "rep",
        parameters.single().code,
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

/** The function returns a `PackedAsyncResult`. */
private class AbiLiftAsyncExport(
  private val functionEncoder: FunctionEncoder,
  private val liftedFunction: KtFunction,
) : KtFunction {
  private val parentService: AbiService
    get() = functionEncoder.service
  private val abiFunction: AbiFunction
    get() = functionEncoder.function

  private val result = abiFunction.result as AbiFunction.Result.CallTaskReturn

  init {
    require(abiFunction.name is FunctionName.AsyncExport)
  }

  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val packedAsyncResultName = codeBuilder.newName("packedAsyncResult")
    codeBuilder.controlFlow(
      "val %N = %L.launchTask",
      packedAsyncResultName,
      codeBuilder.bridge,
    ) {
      val p = parameters.iterator()
      val liftedParametersValues = functionEncoder.liftParameters(p)

      check(!p.hasNext()) {
        "unexpected parameter count"
      }

      codeBuilder.platform.afterLiftParameters()

      val resultValue = liftedFunction.call(
        null,
        liftedParametersValues,
      ).makeImmediate()

      val taskReturn = AbiLower(
        resourceId = null,
        functionEncoder = FunctionEncoder(
          collector = functionEncoder.collector,
          service = parentService,
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
  private val abiFunction: AbiFunction
    get() = functionEncoder.function

  init {
    require(abiFunction.name is FunctionName.AsyncImport)
  }

  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val packedSubtaskResultName = codeBuilder.newName("packedSubtaskResult")
    codeBuilder.controlFlow(
      "val %N = %L.launchSubtask",
      packedSubtaskResultName,
      codeBuilder.bridge,
    ) {
      val p = parameters.iterator()
      val liftedParametersValues = functionEncoder.liftParameters(p)
      val liftedPointerParameterValue = functionEncoder.liftPointerParameter(p)

      check(!p.hasNext()) {
        "unexpected parameter count"
      }

      codeBuilder.platform.afterLiftParameters()

      val resultValue = liftedFunction.call(
        null,
        liftedParametersValues,
      ).makeImmediate()

      functionEncoder.lowerResult(resultValue, liftedPointerParameterValue)
    }

    return CodeBlockExpression(
      type = CoreType.I32.kotlinCoreType,
      nameHint = "packedSubtaskResult",
      code = CodeBlock.of("%N.value.toInt()", packedSubtaskResultName),
      immediate = true,
    )
  }
}

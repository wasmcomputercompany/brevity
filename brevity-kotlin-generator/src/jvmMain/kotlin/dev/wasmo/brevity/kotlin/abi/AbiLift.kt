package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.INT
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Parameters.Flattened
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Parameters.Stored
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.PointerParameter
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.PointerReturn
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.SingleCoreValueReturn
import dev.wasmo.brevity.kotlin.abi.AbiFunction.Result.Void
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.withMemoryAllocator
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction
import dev.wasmo.brevity.kotlin.expressions.makeImmediate
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

context(codeBuilder: CodeBuilder)
fun AbiLift(
  parentService: AbiService,
  abiFunction: AbiFunction,
  resourceImplementation: ClassName? = null,
): KtFunction {
  return when {
    abiFunction.name is FunctionName.AsyncExport -> AbiLiftAsyncExport(
      parentService = parentService,
      abiFunction = abiFunction,
      liftedFunction = CallLifted(parentService, abiFunction),
    )

    abiFunction.name is FunctionName.AsyncImport -> AbiLiftAsyncImport(
      abiFunction = abiFunction,
      liftedFunction = CallLifted(parentService, abiFunction),
    )

    abiFunction.name is FunctionName.ResourceNew -> AbiResourceNewLift(
      parentService = parentService,
      abiFunction = abiFunction,
      resourceImplementation = resourceImplementation!!,
    )

    abiFunction.name is FunctionName.ResourceDrop -> AbiResourceDropLift(
      parentService = parentService,
      abiFunction = abiFunction,
    )

    abiFunction.name is FunctionName.AsyncExportCallback -> object : KtFunction {
      context(codeBuilder: CodeBuilder)
      override fun call(
        receiver: KtExpression?,
        parameters: List<KtExpression>,
      ): KtExpression {
        codeBuilder.addStatement("TODO(%S)", "implement AbiLift for AsyncLiftCallbacks")
        return KtExpression.Unit
      }
    }

    abiFunction.name is FunctionName.TaskReturn -> AbiTaskReturnLift(
      abiFunction = abiFunction,
    )

    else -> AbiSyncLift(
      abiFunction = abiFunction,
      liftedFunction = CallLifted(parentService, abiFunction),
    )
  }
}

private class AbiSyncLift(
  private val abiFunction: AbiFunction,
  private val liftedFunction: KtFunction,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val p = parameters.iterator()
    val liftedParametersValues = abiFunction.liftParameters(p)
    val liftedPointerParameterValue = abiFunction.liftPointerParameter(p)

    check(!p.hasNext()) {
      "unexpected parameter count"
    }

    codeBuilder.platform.afterLiftParameters()

    val resultValue = liftedFunction.call(
      null,
      liftedParametersValues,
    ).makeImmediate()

    return abiFunction.lowerResult(resultValue, liftedPointerParameterValue)
  }
}

private class AbiTaskReturnLift(
  private val abiFunction: AbiFunction,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val p = parameters.iterator()
    val liftedParameterValues = abiFunction.liftParameters(p)
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
  private val parentService: AbiService,
  private val abiFunction: AbiFunction,
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
        "%L.newResource(%T(%N = %L))",
        codeBuilder.bridge,
        resourceImplementation,
        "rep",
        parameters.single().code,
      ),
    )
  }
}

private class AbiResourceDropLift(
  private val parentService: AbiService,
  private val abiFunction: AbiFunction,
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
  private val parentService: AbiService,
  private val abiFunction: AbiFunction,
  private val liftedFunction: KtFunction,
) : KtFunction {
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
      val liftedParametersValues = abiFunction.liftParameters(p)

      check(!p.hasNext()) {
        "unexpected parameter count"
      }

      codeBuilder.platform.afterLiftParameters()

      val resultValue = liftedFunction.call(
        null,
        liftedParametersValues,
      ).makeImmediate()

      val taskReturn = AbiLower(
        abiService = parentService,
        abiFunction = result.taskReturn,
        resourceId = null,
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
  private val abiFunction: AbiFunction,
  private val liftedFunction: KtFunction,
) : KtFunction {
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
      val liftedParametersValues = abiFunction.liftParameters(p)
      val liftedPointerParameterValue = abiFunction.liftPointerParameter(p)

      check(!p.hasNext()) {
        "unexpected parameter count"
      }

      codeBuilder.platform.afterLiftParameters()

      val resultValue = liftedFunction.call(
        null,
        liftedParametersValues,
      ).makeImmediate()

      abiFunction.lowerResult(resultValue, liftedPointerParameterValue)
    }

    return CodeBlockExpression(
      type = CoreType.I32.kotlinCoreType,
      nameHint = "packedSubtaskResult",
      code = CodeBlock.of("%N.value.toInt()", packedSubtaskResultName),
      immediate = true,
    )
  }
}

context(codeBuilder: CodeBuilder)
private fun AbiFunction.liftParameters(
  p: Iterator<KtExpression>,
): List<KtExpression> {
  val values = when (parameters) {
    is Flattened -> parameters.parameters.map { flatParameter ->
      flatParameter.encoder.liftFlat(
        values = flatParameter.encoder.coreTypes.map { p.next().code },
      )
    }

    is Stored -> parameters.loadAll(
      baseAddress = codeBuilder.platform.liftAddress(p.next().code),
    )
  }

  check(parameters.parameters.size == values.size)

  return parameters.parameters.zip(values).map { (flatParameter, value) ->
    CodeBlockExpression(
      type = flatParameter.type,
      nameHint = flatParameter.name,
      code = value,
    )
  }
}

context(codeBuilder: CodeBuilder)
private fun AbiFunction.liftPointerParameter(
  p: Iterator<KtExpression>,
): CodeBlock? {
  if (result !is PointerParameter) return null

  codeBuilder.addStatement(
    "val %N = %L",
    result.parameter.name,
    codeBuilder.platform.liftAddress(p.next().code),
  )
  return CodeBlock.of("%N", result.parameter.name)
}

context(codeBuilder: CodeBuilder)
private fun AbiFunction.lowerResult(
  resultValue: KtExpression,
  pointerParameterValue: CodeBlock?,
): KtExpression {
  return when (result) {
    Void -> KtExpression.Unit

    is SingleCoreValueReturn -> {
      withMemoryAllocator(
        lowerAllocates = result.value.encoder.lowerAllocates,
      ) { memoryAllocator ->
        CodeBlockExpression(
          type = result.value.encoder.coreTypes.single().kotlinCoreType,
          nameHint = "result",
          code = result.value.encoder.lowerFlat(
            memoryAllocator = memoryAllocator,
            value = resultValue.code,
          ).single(),
        )
      }
    }

    is PointerReturn -> {
      withMemoryAllocator(
        lowerAllocates = true,
      ) { memoryAllocator ->
        val codeBuilder = contextOf<CodeBuilder>()
        val addressName = codeBuilder.newName("address")
        codeBuilder.addStatement(
          "val %N = %L",
          addressName,
          memoryAllocator!!.allocate("%L", result.value.encoder.byteCount),
        )
        val resultAddressValue = CodeBlock.of("%N", addressName)
        result.value.encoder.store(
          memoryAllocator = memoryAllocator,
          baseAddress = resultAddressValue,
          value = resultValue.code,
        )
        CodeBlockExpression(
          type = CoreType.Pointer.kotlinCoreType,
          nameHint = "resultAddress",
          code = codeBuilder.platform.lowerAddress(resultAddressValue),
        )
      }
    }

    is PointerParameter -> {
      withMemoryAllocator(
        lowerAllocates = result.value.encoder.lowerAllocates,
      ) { memoryAllocator ->
        result.value.encoder.store(
          memoryAllocator = memoryAllocator,
          baseAddress = pointerParameterValue!!,
          value = resultValue.code,
        )
        KtExpression.Unit
      }
    }

    is AbiFunction.Result.CallTaskReturn -> error("unexpected call")
  }
}

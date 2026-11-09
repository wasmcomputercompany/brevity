package dev.wasmo.brevity.kotlin.abi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dev.wasmo.brevity.FunctionName
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.KtExpression
import dev.wasmo.brevity.kotlin.expressions.KtFunction
import dev.wasmo.brevity.kotlin.expressions.makeImmediate
import dev.wasmo.brevity.kotlin.generator.FunctionEncoder
import dev.wasmo.brevity.kotlin.generator.Symbols
import dev.wasmo.brevity.kotlin.generator.TypeNamer
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

context(codeBuilder: CodeBuilder)
fun AbiLift(
  functionEncoder: FunctionEncoder,
): KtFunction {
  return when (functionEncoder.function.name) {
    is FunctionName.ResourceNew -> AbiResourceNewLift(
      functionEncoder = functionEncoder,
    )

    is FunctionName.ResourceDrop -> AbiResourceDropLift(
      functionEncoder = functionEncoder,
    )

    is FunctionName.StreamNew -> AbiStreamNewLift(
      functionEncoder = functionEncoder,
      stream = functionEncoder.stream!!,
    )

    is FunctionName.StreamIo -> AbiStreamIoLift(
      functionName = functionEncoder.function.name,
      stream = functionEncoder.stream!!,
    )

    is FunctionName.StreamCancel -> AbiStreamCancelLift(
      functionName = functionEncoder.function.name,
      stream = functionEncoder.stream!!,
    )

    is FunctionName.StreamDrop -> AbiStreamDropLift(
      functionName = functionEncoder.function.name,
      stream = functionEncoder.stream!!,
    )

    else -> when (functionEncoder.function.name.transform) {
      FunctionName.Transform.AsyncExport -> AbiLiftAsyncExport(
        functionEncoder = functionEncoder,
        liftedFunction = CallLifted(functionEncoder),
      )

      FunctionName.Transform.AsyncImport -> AbiLiftAsyncImport(
        functionEncoder = functionEncoder,
        liftedFunction = CallLifted(functionEncoder),
      )

      FunctionName.Transform.AsyncExportCallback -> AbiAsyncExportCallbackLift()
      FunctionName.Transform.TaskReturn -> AbiTaskReturnLift(
        functionEncoder = functionEncoder,
      )

      else -> AbiSyncLift(
        functionEncoder = functionEncoder,
        liftedFunction = CallLifted(functionEncoder),
      )
    }
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
    require(functionEncoder.function.name.transform == FunctionName.Transform.AsyncExport)
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
          encoderFunctionCollector = functionEncoder.encoderFunctionCollector,
          streamBindingsCollector = functionEncoder.streamBindingsCollector,
          service = functionEncoder.service,
          stream = functionEncoder.stream,
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
    require(functionEncoder.function.name.transform == FunctionName.Transform.AsyncImport)
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

private class AbiStreamNewLift(
  private val functionEncoder: FunctionEncoder,
  private val stream: AbiStream,
) : KtFunction {
  private val typeNamer: TypeNamer
    get() = functionEncoder.streamBindingsCollector.typeNamer

  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)
    codeBuilder.platform.afterLiftParameters()

    val streamPairName = codeBuilder.newName("streamPair")
    val resultName = codeBuilder.newName("result")
    codeBuilder.addStatement(
      "val %N = %T()",
      streamPairName,
      Symbols.Brevity.streamPair(stream.cardinality).parameterizedBy(stream.elementType),
    )
    codeBuilder.addStatement(
      "%N.receive.initBinding(%L, %L)",
      streamPairName,
      codeBuilder.bridge,
      typeNamer.streamBindingInstance(this.stream.irStream.type),
    )
    codeBuilder.addStatement(
      "val %N = (%N.send.rep.toLong() shl 32) or %N.receive.rep.toLong()",
      resultName,
      streamPairName,
      streamPairName,
    )
    return CodeBlockExpression(
      type = LONG,
      nameHint = "result",
      code = CodeBlock.of("%N", resultName),
    )
  }
}

private class AbiStreamIoLift(
  private val functionName: FunctionName.StreamIo,
  private val stream: AbiStream,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val streamName = codeBuilder.newName("stream")
    val streamType = when (functionName.op) {
      FunctionName.StreamOp.Read -> Symbols.Brevity.receiveStream(stream.cardinality)
      FunctionName.StreamOp.Write -> Symbols.Brevity.sendStream(stream.cardinality)
    }
    codeBuilder.addStatement(
      "val %N = %L.getImportedResource<%T>(%L)",
      streamName,
      codeBuilder.bridge,
      streamType.parameterizedBy(stream.elementType),
      parameters[0].code,
    )
    codeBuilder.platform.afterLiftParameters()

    val resultName = codeBuilder.newName("result")
    codeBuilder.add(
      "val %N = %N.%N(%L",
      resultName,
      streamName,
      functionName.op.verb.name,
      parameters[1].code,
    )
    if (functionName.cardinality == TypeName.Stream.Cardinality.Stream) {
      codeBuilder.add(", %L", parameters[2].code)
    }
    codeBuilder.add(")\n")
    return CodeBlockExpression(
      type = INT,
      nameHint = "result",
      code = CodeBlock.of("%N", resultName),
    )
  }
}

private class AbiStreamCancelLift(
  private val functionName: FunctionName.StreamCancel,
  private val stream: AbiStream,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val streamName = codeBuilder.newName("stream")
    val streamType = when (functionName.op) {
      FunctionName.StreamOp.Read -> Symbols.Brevity.receiveStream(stream.cardinality)
      FunctionName.StreamOp.Write -> Symbols.Brevity.sendStream(stream.cardinality)
    }
    codeBuilder.addStatement(
      "val %N = %L.getImportedResource<%T>(%L)",
      streamName,
      codeBuilder.bridge,
      streamType.parameterizedBy(stream.elementType),
      parameters[0].code,
    )
    codeBuilder.platform.afterLiftParameters()

    val resultName = codeBuilder.newName("result")
    codeBuilder.addStatement(
      "val %N = %N.cancel()",
      resultName,
      streamName,
    )

    return CodeBlockExpression(
      type = INT,
      nameHint = "result",
      code = CodeBlock.of("%N", resultName),
    )
  }
}

private class AbiStreamDropLift(
  private val functionName: FunctionName.StreamDrop,
  private val stream: AbiStream,
) : KtFunction {
  context(codeBuilder: CodeBuilder)
  override fun call(
    receiver: KtExpression?,
    parameters: List<KtExpression>,
  ): KtExpression {
    check(receiver == null)

    val streamName = codeBuilder.newName("stream")
    val streamType = when (functionName.op) {
      FunctionName.StreamOp.Read -> Symbols.Brevity.receiveStream(stream.cardinality)
      FunctionName.StreamOp.Write -> Symbols.Brevity.sendStream(stream.cardinality)
    }
    codeBuilder.addStatement(
      "val %N = %L.getImportedResource<%T>(%L)",
      streamName,
      codeBuilder.bridge,
      streamType.parameterizedBy(stream.elementType),
      parameters[0].code,
    )
    codeBuilder.platform.afterLiftParameters()
    codeBuilder.addStatement("%N.close()", streamName)

    return KtExpression.Unit
  }
}

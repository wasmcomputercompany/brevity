package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.CodeBlock
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.withMemoryAllocator
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Parameters.Flattened
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Parameters.Stored
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Result.PointerParameter
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Result.PointerReturn
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Result.SingleCoreValueReturn
import dev.wasmo.brevity.kotlin.expressions.AbiFunction.Result.Void
import dev.wasmo.brevity.kotlin.generator.kotlinCoreType

class AbiLift(
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
    val liftedReceiverValue = abiFunction.liftReceiver(p)
    val liftedParametersValues = abiFunction.liftParameters(p)
    val liftedPointerParameterValue = abiFunction.liftPointerParameter(p)

    check(!p.hasNext()) {
      "unexpected parameter count"
    }

    codeBuilder.platform.afterLiftParameters()

    val resultValue = liftedFunction.call(
      liftedReceiverValue,
      liftedParametersValues,
    ).makeImmediate()

    return abiFunction.lowerResult(resultValue, liftedPointerParameterValue)
  }
}

context(codeBuilder: CodeBuilder)
private fun AbiFunction.liftReceiver(
  p: Iterator<KtExpression>,
): KtExpression {
  val receiverValue = when (parent) {
    is FunctionParent.Resource -> {
      CodeBlockExpression(
        type = parent.kotlinType,
        nameHint = "receiver",
        code = codeBuilder.platform.liftResource(
          id = p.next().code,
          handleType = parent.type,
        ),
      )
    }

    is FunctionParent.Interface -> codeBuilder.platform.getParent(parent)
    is FunctionParent.World -> codeBuilder.platform.getParent(parent)
  }

  return receiverValue.makeImmediate()
}

context(codeBuilder: CodeBuilder)
private fun AbiFunction.liftParameters(
  p: Iterator<KtExpression>,
): List<KtExpression> {
  val codeBlocks = when (parameters) {
    is Flattened -> {
      parameters.parameters.map { flatParameter ->
        flatParameter.encoder.liftFlat(
          values = flatParameter.encoder.coreTypes.map { p.next().code },
        )
      }
    }

    is Stored -> {
      parameters.loadAll(
        baseAddress = codeBuilder.platform.liftAddress(p.next().code),
      )
    }
  }

  return parameters.liftedSpecs.zip(codeBlocks).map { (spec, code) ->
    CodeBlockExpression(
      type = spec.type,
      nameHint = spec.name,
      code = code,
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
          type = result.loweredType.kotlinCoreType,
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
  }
}

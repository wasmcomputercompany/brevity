package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.valType
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Invoker
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Receiver

class HostFunctionFactory(
  private val hostPlatform: HostPlatform,
) {
  /** Returns a function that calls the guest. It implements the friendly API. */
  fun callGuest(
    bridge: CodeBlock,
    function: BridgeFunction,
  ): FunSpec {
    val invoker = object : Invoker {
      context(codeBuilder: CodeBuilder)
      override fun invoke(parameterValues: List<CodeBlock>) {
        val result = function.loweredResult.result
        if (result != null) {
          codeBuilder.add("val %N = ", result.arrayName)
        }
        codeBuilder.add("%N.apply(⇥\n", function.kotlinName)
        for (parameterValue in parameterValues) {
          codeBuilder.add("%L,\n", parameterValue)
        }
        codeBuilder.add("⇤)\n")
        if (result != null) {
          codeBuilder.addStatement(
            "val %N = %N[%L]",
            result.loweredName,
            result.arrayName,
            0,
          )
        }
      }
    }

    return function.outboundFunction(
      bridge = bridge,
      platform = hostPlatform,
      invoker,
    )
  }

  /** Adds a host function using the Chicory API. */
  fun declareHost(
    bridge: CodeBlock,
    store: CodeBlock,
    function: BridgeFunction,
  ): CodeBlock {
    check(function.liftedReceiver !is Receiver.OutboundInstance)

    val codeBuilder = CodeBuilder(
      bridge = bridge,
      platform = hostPlatform,
      nameAllocator = function.nameAllocator(),
    )

    context(codeBuilder) {
      if (!function.isSupported) {
        return CodeBlock.of("/* TODO: ${function.kotlinName} */\n")
      }

      val returnValue = function.liftInboundCall(
        parameterValues = function.loweredParameterTypes.indices.map { index ->
          CodeBlock.of("%N[%L]", "args", index)
        },
      )

      codeBuilder.add("return@%T longArrayOf(", Symbols.ChicoryRuntime.WasmFunctionHandle)
      if (returnValue != null) {
        codeBuilder.add("%L", returnValue)
      }
      codeBuilder.add(")")
    }

    return addChicoryFunction(
      store = store,
      moduleName = function.functionName.moduleName,
      abiName = function.functionName.abiName,
      parameterTypes = function.loweredParameterTypes,
      returnType = function.loweredReturnType,
      body = codeBuilder.build(),
    )
  }

  private fun addChicoryFunction(
    store: CodeBlock,
    moduleName: String?,
    abiName: String,
    parameterTypes: List<CoreType>,
    returnType: CoreType?,
    body: CodeBlock,
  ): CodeBlock {
    return CodeBlock.of(
      """
      |%L.addFunction(
      |  %T(
      |    %L,
      |    %S,
      |    %T.of(
      |      listOf(%L),
      |      listOf(%L),
      |    ),
      |    %T { instance, args ->
      |      ⇥⇥⇥%L⇤⇤⇤
      |    },
      |  )
      |)
      |
      """.trimMargin(),
      store,
      Symbols.ChicoryRuntime.HostFunction,
      moduleName?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"),
      abiName,
      Symbols.ChicoryRuntime.FunctionType,
      parameterTypes.joinToCode { it.valType },
      returnType?.valType ?: CodeBlock.of(""),
      Symbols.ChicoryRuntime.WasmFunctionHandle,
      body,
    )
  }
}

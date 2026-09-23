package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.encoders.valType
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Invoker
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Receiver
import java.util.concurrent.atomic.AtomicBoolean

internal class HostFunctionFactory(
  hostPlatform: HostPlatform,
  private val bridge: CodeBlock,
  private val function: BridgeFunction,
) {
  private val used = AtomicBoolean()

  private val codeBuilder = CodeBuilder(
    bridge = bridge,
    platform = hostPlatform,
    nameAllocator = function.nameAllocator,
  )

  /** Returns a function that calls the guest. It implements the friendly API. */
  fun callGuest(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    val invoker = object : Invoker {
      context(codeBuilder: CodeBuilder)
      override fun invoke(parameterValues: List<CodeBlock>) {
        if (function.result != null) {
          codeBuilder.add("val %N = ", function.result.arrayName)
        }
        codeBuilder.add("%N.apply(⇥\n", function.kotlinName)
        for (loweredParameterValue in parameterValues) {
          codeBuilder.add("%L,\n", loweredParameterValue)
        }
        codeBuilder.add("⇤)\n")
        if (function.result != null) {
          codeBuilder.addStatement(
            "val %N = %N[%L]",
            function.result.loweredName,
            function.result.arrayName,
            0,
          )
        }
      }
    }

    return function.outboundFunction(codeBuilder, invoker)
  }

  /** Adds a host function using the Chicory API. */
  fun declareHost(
    store: CodeBlock,
  ): CodeBlock {
    require(used.compareAndSet(false, true)) { "cannot be reused" }
    require(function.liftedReceiver !is Receiver.OutboundInstance)

    context(codeBuilder) {
      if (!function.function.isSupported) {
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
        function.functionName.moduleName?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"),
        function.functionName.abiName,
        Symbols.ChicoryRuntime.FunctionType,
        function.loweredParameterTypes.joinToCode { it.valType },
        function.loweredReturnType?.valType ?: CodeBlock.of(""),
        Symbols.ChicoryRuntime.WasmFunctionHandle,
        codeBuilder.build(),
      )
    }
  }
}

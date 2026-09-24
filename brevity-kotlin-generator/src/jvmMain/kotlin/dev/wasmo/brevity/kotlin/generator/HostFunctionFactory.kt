package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.encoders.valType
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Receiver
import java.util.concurrent.atomic.AtomicBoolean

internal class HostFunctionFactory(
  private val kotlinMapper: KotlinMapper,
  hostPlatform: HostPlatform,
  private val bridge: CodeBlock,
  private val function: BridgeFunction,
  private val supportAsync: Boolean,
) {
  private val used = AtomicBoolean()

  private val value: IrFunction
    get() = function.function

  private val receiver: Receiver = function.liftedReceiver

  private val nameAllocator: NameAllocator
    get() = function.nameAllocator

  private val codeBuilder = CodeBuilder(
    bridge = bridge,
    platform = hostPlatform,
    nameAllocator = nameAllocator,
  )

  /** Returns a function that calls the guest. It implements the friendly API. */
  fun callGuest(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }
    require(receiver !is Receiver.InboundInstance)

    return FunSpec.builder(function.kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        context(codeBuilder) {
          if (value.async && supportAsync) {
            addModifiers(KModifier.SUSPEND)
          }
          addParameters(function.liftedParameterSpecs)
          val loweredParameterValues = function.lowerParameterValues()

          if (function.result != null) {
            codeBuilder.add("val %N = ", function.result.arrayName)
          }
          codeBuilder.add("%N.apply(⇥\n", function.kotlinName)
          for (loweredParameterValue in loweredParameterValues) {
            codeBuilder.add(
              "%L,\n",
              loweredParameterValue,
            )
          }
          codeBuilder.add("⇤)\n")

          if (function.result != null) {
            codeBuilder.addStatement(
              "val %N = %N[%L]",
              function.result.name,
              function.result.arrayName,
              0,
            )
            returns(kotlinMapper.get(function.result.type))
          }

          val returnValue = function.liftReturnValue()

          if (returnValue != null) {
            codeBuilder.add(
              "return %L",
              returnValue,
            )
          }
        }
      }
      .addCode(codeBuilder.build())
      .build()
  }

  /** Adds a host function using the Chicory API. */
  fun declareHost(
    store: CodeBlock,
  ): CodeBlock {
    require(used.compareAndSet(false, true)) { "cannot be reused" }
    require(receiver !is Receiver.OutboundInstance)

    context(codeBuilder) {
      if (!value.isSupported) return CodeBlock.of("/* TODO: ${function.kotlinName} */\n")

      val runtimeParameterValues = function.loweredParameterTypes.indices.map { index ->
        CodeBlock.of("%N[%L]", "args", index)
      }

      val liftedParameterValues = function.liftParameterValues(runtimeParameterValues)

      codeBuilder.platform.afterLiftParameters()

      val self = nameAllocator.newName("self")
      codeBuilder.addStatement("val %N = %L", self, liftedParameterValues.receiverValue)
      if (function.result != null) {
        codeBuilder.add("val %N = ", function.result.name)
      }
      codeBuilder.add("%N.%N(⇥", self, function.kotlinName)
      if (value.parameters.isNotEmpty()) {
        codeBuilder.add("\n")
      }
      for ((index, parameter) in value.parameters.withIndex()) {
        codeBuilder.add(
          "%N = %L,\n",
          nameAllocator[parameter.name],
          liftedParameterValues.parameterValues[index],
        )
      }
      codeBuilder.add("⇤)\n")

      codeBuilder.platform.beforeLowerReturnValue()

      val returnValue = function.lowerReturnValue(
        liftedParameterValues = liftedParameterValues,
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
        value.functionName.moduleName?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"),
        value.functionName.abiName,
        Symbols.ChicoryRuntime.FunctionType,
        function.loweredParameterTypes.joinToCode { it.valType },
        function.loweredReturnType?.valType ?: CodeBlock.of(""),
        Symbols.ChicoryRuntime.WasmFunctionHandle,
        codeBuilder.build(),
      )
    }
  }
}

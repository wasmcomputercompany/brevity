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
import dev.wasmo.brevity.kotlin.encoders.coreTypeToLong
import dev.wasmo.brevity.kotlin.encoders.longToCoreType
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
          val loweredParameterTypes = function.loweredParameterTypes

          if (function.result != null) {
            codeBuilder.add("val %N = ", function.result.name)
          }
          codeBuilder.add("%N.apply(⇥\n", function.kotlinName)
          for (p in loweredParameterValues.indices) {
            codeBuilder.add(
              "%L,\n",
              coreTypeToLong(loweredParameterValues[p], loweredParameterTypes[p]),
            )
          }
          codeBuilder.add("⇤)\n")

          if (function.result != null) {
            returns(kotlinMapper.get(function.result.type))
          }

          val returnValue = function.liftReturnValue { name, type ->
            longToCoreType(name, 0, type)
          }

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

      val loweredParameterTypes = function.loweredParameterTypes
      val loweredParameterValues = loweredParameterTypes.withIndex().map { (index, coreType) ->
        longToCoreType("args", index, coreType)
      }

      val liftedParameterValues = function.liftParameterValues(
        loweredParameterValues = loweredParameterValues,
      )

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

      val returnValType = function.loweredReturnType
      val returnValue = function.lowerReturnValue(liftedParameterValues)

      if (returnValue != null) {
        codeBuilder.add(
          "return@%T longArrayOf(%L)",
          Symbols.ChicoryRuntime.WasmFunctionHandle,
          coreTypeToLong(returnValue, returnValType!!),
        )
      } else {
        codeBuilder.add(
          "return@%T longArrayOf()",
          Symbols.ChicoryRuntime.WasmFunctionHandle,
        )
      }

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
        loweredParameterTypes.joinToCode { it.valType },
        returnValType?.valType ?: CodeBlock.of(""),
        Symbols.ChicoryRuntime.WasmFunctionHandle,
        codeBuilder.build(),
      )
    }
  }
}

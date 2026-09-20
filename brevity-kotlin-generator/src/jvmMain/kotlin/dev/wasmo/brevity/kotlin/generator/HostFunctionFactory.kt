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
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.coreTypeToLong
import dev.wasmo.brevity.kotlin.encoders.longToCoreType
import dev.wasmo.brevity.kotlin.encoders.valType
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.LoweredParameters
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
          addParameters(function.liftedParameters)
          val loweredParameterValues = function.lowerParameterValues()

          if (function.result != null) {
            codeBuilder.add("val %N = ", function.result.name)
          }
          codeBuilder.add("%N.apply(⇥\n", function.kotlinName)
          for ((parameter, coreType) in loweredParameterValues) {
            codeBuilder.add("%L,\n", coreTypeToLong(parameter, coreType))
          }
          codeBuilder.add("⇤)\n")

          if (function.result != null) {
            returns(kotlinMapper.get(function.result.type))
            val returnValue = when (function.result.encoder.coreTypes.size) {
              1 -> function.result.encoder.liftFlat(
                values = listOf(
                  longToCoreType(function.result.name, 0, function.result.encoder.coreTypes.single()),
                ),
              )

              else -> function.result.encoder.load(
                longToCoreType(function.result.name, 0, CoreType.Pointer),
              )
            }
            codeBuilder.add("return %L", returnValue)
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

      val coreParameterTypes = buildList {
        if (receiver is Receiver.Id) {
          add(CoreType.I32)
        }
        when (function.loweredParameters) {
          is LoweredParameters.Flattened -> {
            for (coreParameter in function.loweredParameters.parameters) {
              addAll(coreParameter.encoder.coreTypes)
            }
          }

          is LoweredParameters.Stored -> {
            add(CoreType.Pointer)
          }
        }
        if (function.result?.pointerParameter != null) {
          add(CoreType.I32)
        }
      }

      var argIndex = 0
      val receiverValue = when (receiver) {
        is Receiver.Id -> CodeBlock.of(
          "%L.%M<%T>(%L)",
          bridge,
          Symbols.Brevity.HostBridgeGet,
          kotlinMapper.getAbiClassName(receiver.type),
          longToCoreType("args", argIndex++, CoreType.I32),
        )
        is Receiver.InboundInstance -> receiver.codeBlock
      }
      val liftedParameterValues = when (function.loweredParameters) {
        is LoweredParameters.Flattened -> {
          function.loweredParameters.parameters.map { coreParameter ->
            coreParameter.encoder.liftFlat(
              values = coreParameter.encoder.coreTypes.map { coreType ->
                longToCoreType("args", argIndex++, coreType)
              },
            )
          }
        }

        is LoweredParameters.Stored -> {
          function.loweredParameters.loadAll(
            baseAddress = longToCoreType("args", argIndex++, CoreType.Pointer),
          )
        }
      }

      val self = nameAllocator.newName("self")
      codeBuilder.addStatement("val %N = %L", self, receiverValue)
      if (function.result != null) {
        codeBuilder.add("val %N = ", function.result.name)
      }
      codeBuilder.add("%N.%N(⇥", self, function.kotlinName)
      if (value.parameters.isNotEmpty()) {
        codeBuilder.add("\n")
      }
      for ((index, parameter) in value.parameters.withIndex()) {
        codeBuilder.add("%N = %L,\n", nameAllocator[parameter.name], liftedParameterValues[index])
      }
      codeBuilder.add("⇤)\n")

      val returnValType: CoreType?
      if (function.result != null) {
        when {
          function.result.pointerParameter != null -> {
            codeBuilder.addStatement(
              "val %N = %L",
              function.result.pointerParameter.name,
              longToCoreType("args", argIndex++, CoreType.Pointer),
            )
            function.result.encoder.store(
              baseAddress = CodeBlock.of("%N", function.result.pointerParameter.name),
              value = CodeBlock.of("%N", function.result.name),
            )
            returnValType = null
            codeBuilder.add("return@%T longArrayOf()", Symbols.ChicoryRuntime.WasmFunctionHandle)
          }

          else -> {
            val loweredReturnValues = function.result.encoder.lowerFlat(
              value = CodeBlock.of("%N", function.result.name),
            )
            returnValType = function.result.encoder.coreTypes.single()
            codeBuilder.add(
              "return@%T longArrayOf(%L)",
              Symbols.ChicoryRuntime.WasmFunctionHandle,
              coreTypeToLong(loweredReturnValues.single(), returnValType),
            )
          }
        }
      } else {
        returnValType = null
        codeBuilder.add("return@%T longArrayOf()", Symbols.ChicoryRuntime.WasmFunctionHandle)
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
        coreParameterTypes.joinToCode { it.valType },
        returnValType?.valType ?: CodeBlock.of(""),
        Symbols.ChicoryRuntime.WasmFunctionHandle,
        codeBuilder.build(),
      )
    }
  }
}

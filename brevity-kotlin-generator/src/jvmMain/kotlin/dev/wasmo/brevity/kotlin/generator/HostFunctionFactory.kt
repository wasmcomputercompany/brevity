package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.HostPlatform
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.coreTypeToLong
import dev.wasmo.brevity.kotlin.encoders.longToCoreType
import dev.wasmo.brevity.kotlin.encoders.valType
import dev.wasmo.brevity.kotlin.generator.HostGenerator.Receiver
import java.util.concurrent.atomic.AtomicBoolean

internal class HostFunctionFactory(
  encoderFactory: EncoderFactory,
  private val value: IrFunction,
  private val bridge: CodeBlock,
) {
  private val used = AtomicBoolean()

  private val nameAllocator = NameAllocator().apply {
    // Pre-allocate the names we'll need.
    for (parameter in value.parameters) {
      newName(parameter.kotlinName, parameter.name)
    }
  }

  private val coreValueFactory = CoreValueFactory(
    encoderFactory = encoderFactory,
    nameAllocator = nameAllocator,
  )
  private val coreParameters = value.parameters.map { coreValueFactory.parameter(it.name, it.type) }
  private val coreResult = value.returnType?.let { coreValueFactory.result(it) }

  private val codeBuilder = CodeBuilder(
    bridge = bridge,
    platform = HostPlatform,
    nameAllocator = nameAllocator,
  )

  /** Returns a function that calls the guest. It implements the friendly API. */
  fun callGuest(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    return FunSpec.builder(value.kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        context(codeBuilder) {
          val longParameters = mutableListOf<CodeBlock>()
          for ((p, parameter) in value.parameters.withIndex()) {
            val coreParameter = coreParameters[p]
            addParameter(nameAllocator[parameter.name], parameter.type.kotlinApi)
            val loweredParameters = coreParameter.encoder.lowerFlat(
              value = CodeBlock.of("%N", nameAllocator[parameter.name]),
            )
            for ((v, coreType) in coreParameter.encoder.coreTypes.withIndex()) {
              longParameters += coreTypeToLong(loweredParameters[v], coreType)
            }
          }

          if (coreResult != null) {
            codeBuilder.add("val %N = ", coreResult.name)
          }
          codeBuilder.add("%N.apply(⇥\n", value.kotlinName)
          for (longParameter in longParameters) {
            codeBuilder.add("%L,\n", longParameter)
          }
          codeBuilder.add("⇤)\n")

          if (coreResult != null) {
            returns(coreResult.type.kotlinApi)
            val returnValue = when (coreResult.encoder.coreTypes.size) {
              1 -> coreResult.encoder.liftFlat(
                values = listOf(
                  longToCoreType(coreResult.name, 0, coreResult.encoder.coreTypes.single()),
                ),
              )

              else -> coreResult.encoder.load(
                longToCoreType(coreResult.name, 0, CoreType.Pointer),
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
    receiver: Receiver,
  ): CodeBlock {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    context(codeBuilder) {
      if (!value.isSupported) return CodeBlock.of("/* TODO: ${value.kotlinName} */\n")

      val coreParameterTypes = buildList {
        if (receiver is Receiver.Id) {
          add(CoreType.I32)
        }
        for (coreParameter in coreParameters) {
          addAll(coreParameter.encoder.coreTypes)
        }
        if (coreResult?.parameter != null) {
          add(CoreType.I32)
        }
      }

      var argIndex = 0
      val liftedParameterValues = mutableListOf<CodeBlock>()
      val receiverValue = when (receiver) {
        is Receiver.Id -> receiver.codeBlock(longToCoreType("args", argIndex++, CoreType.I32))
        is Receiver.Instance -> receiver.codeBlock
      }
      for (coreParameter in coreParameters) {
        liftedParameterValues += coreParameter.encoder.liftFlat(
          values = coreParameter.encoder.coreTypes.map { coreType ->
                longToCoreType("args", argIndex++, coreType)
          },
        )
      }

      val self = nameAllocator.newName("self")
      codeBuilder.addStatement("val %N = %L", self, receiverValue)
      if (coreResult != null) {
        codeBuilder.add("val %N = ", coreResult.name)
      }
      codeBuilder.add("%N.%N(⇥", self, value.kotlinName)
      if (value.parameters.isNotEmpty()) {
        codeBuilder.add("\n")
      }
      for ((index, parameter) in value.parameters.withIndex()) {
        codeBuilder.add("%N = %L,\n", nameAllocator[parameter.name], liftedParameterValues[index])
      }
      codeBuilder.add("⇤)\n")

      val returnValType: CoreType?
      if (coreResult != null) {
        when {
          coreResult.parameter != null -> {
            codeBuilder.addStatement(
              "val %N = %L",
              coreResult.parameter.name,
              longToCoreType("args", argIndex++, CoreType.Pointer),
            )
            coreResult.encoder.store(
              baseAddress = CodeBlock.of("%N", coreResult.parameter.name),
              value = CodeBlock.of("%N", coreResult.name)
            )
            returnValType = null
            codeBuilder.add("return@%T longArrayOf()", Symbols.ChicoryRuntime.WasmFunctionHandle)
          }

          else -> {
            val loweredReturnValues = coreResult.encoder.lowerFlat(
              value = CodeBlock.of("%N", coreResult.name),
            )
            returnValType = coreResult.encoder.coreTypes.single()
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

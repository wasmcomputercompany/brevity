package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.HostPlatform
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

  private val code = CodeBlock.Builder()

  private val encodeBuilder = RealEncodeBuilder(
    bridge = bridge,
    nameAllocator = nameAllocator,
    code = code,
    platform = HostPlatform,
  )

  /** Returns a function that calls the guest. It implements the friendly API. */
  fun callGuest(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    return FunSpec.builder(value.kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        val longParameters = mutableListOf<CodeBlock>()
        for ((p, parameter) in value.parameters.withIndex()) {
          val coreParameter = coreParameters[p]
          addParameter(nameAllocator[parameter.name], parameter.type.kotlinApi)
          val loweredParameters = encodeBuilder.lower(
            value = CodeBlock.of("%N", nameAllocator[parameter.name]),
            encoder = coreParameter.encoder,
          )
          for ((c, coreType) in coreParameter.encoder.coreTypes.withIndex()) {
            longParameters += coreTypeToLong(loweredParameters[c], coreType)
          }
        }

        if (coreResult != null) {
          code.add("val %N = ", coreResult.name)
        }
        code.add("%N.apply(⇥\n", value.kotlinName)
        for (longParameter in longParameters) {
          code.add("%L,\n", longParameter)
        }
        code.add("⇤)\n")

        if (coreResult != null) {
          returns(coreResult.type.kotlinApi)
          val coreReturnValues = when (coreResult.encoder.coreTypes.size) {
            1 -> {
              val result = longToCoreType(coreResult.name, 0, coreResult.encoder.coreTypes.single())
              listOf(result)
            }

            else -> {
              val result = longToCoreType(coreResult.name, 0, CoreType.Pointer)
              encodeBuilder.unflattenResult(result, coreResult)
            }
          }
          val liftedReturnValue = encodeBuilder.lift(
            values = coreReturnValues,
            encoder = coreResult.encoder,
          )
          code.add("return %L", liftedReturnValue)
        }
      }
      .addCode(code.build())
      .build()
  }

  /** Adds a host function using the Chicory API. */
  fun declareHost(
    store: CodeBlock,
    receiver: Receiver,
  ): CodeBlock {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

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
      liftedParameterValues += encodeBuilder.lift(
        values = coreParameter.encoder.coreTypes.map { coreType ->
          longToCoreType("args", argIndex++, coreType)
        },
        encoder = coreParameter.encoder,
      )
    }

    val self = nameAllocator.newName("self")
    code.addStatement("val %N = %L", self, receiverValue)
    if (coreResult != null) {
      code.add("val %N = ", coreResult.name)
    }
    code.add("%N.%N(⇥", self, value.kotlinName)
    if (value.parameters.isNotEmpty()) {
      code.add("\n")
    }
    for ((index, parameter) in value.parameters.withIndex()) {
      code.add("%N = %L,\n", nameAllocator[parameter.name], liftedParameterValues[index])
    }
    code.add("⇤)\n")

    val returnValType: CoreType?
    if (coreResult != null) {
      val loweredReturnValues = encodeBuilder.lower(
        value = CodeBlock.of("%N", coreResult.name),
        encoder = coreResult.encoder,
      )
      when {
        coreResult.parameter != null -> {
          code.addStatement(
            "val %N = %L",
            coreResult.parameter.name,
            longToCoreType("args", argIndex++, CoreType.Pointer),
          )
          encodeBuilder.storeResultInMemory(
            returnValues = loweredReturnValues,
            coreResult = coreResult,
            address = coreResult.parameter.name,
          )
          returnValType = null
          code.add("return@%T longArrayOf()", Symbols.ChicoryRuntime.WasmFunctionHandle)
        }

        else -> {
          returnValType = coreResult.encoder.coreTypes.single()
          code.add(
            "return@%T longArrayOf(%L)",
            Symbols.ChicoryRuntime.WasmFunctionHandle,
            coreTypeToLong(loweredReturnValues.single(), returnValType),
          )
        }
      }
    } else {
      returnValType = null
      code.add("return@%T longArrayOf()", Symbols.ChicoryRuntime.WasmFunctionHandle)
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
      code.build(),
    )
  }
}

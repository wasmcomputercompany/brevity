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
import dev.wasmo.brevity.kotlin.encoders.valType
import dev.wasmo.brevity.kotlin.generator.HostGenerator.Receiver
import java.util.concurrent.atomic.AtomicBoolean

internal class HostFunctionFactory(
  private val encoderFactory: EncoderFactory,
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

  private val coreParameterFactory = CoreParameter.Factory(
    encoderFactory = encoderFactory,
    nameAllocator = nameAllocator,
  )
  private val coreParameters = value.parameters.map { coreParameterFactory(it.name, it.type) }

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
        val loweredParameters = mutableListOf<CodeBlock>()
        for ((index, parameter) in value.parameters.withIndex()) {
          addParameter(nameAllocator[parameter.name], parameter.type.kotlinApi)
          loweredParameters += encodeBuilder.lower(
            value = CodeBlock.of("%N", nameAllocator[parameter.name]),
            encoder = coreParameters[index].encoder,
          )
        }

        val result = nameAllocator.newName("result")
        val returnType = value.returnType
        if (returnType != null) {
          code.add("val %N = ", result)
        }
        code.add("%N.apply(⇥\n", value.kotlinName)
        for (loweredParameter in loweredParameters) {
          code.add("%L,\n", loweredParameter)
        }
        code.add("⇤)\n")

        if (returnType != null) {
          returns(returnType.kotlinApi)
          val encoder = encoderFactory.get(typeName = returnType)
          val coreReturnValues = when (encoder.coreTypes.size) {
            1 -> listOf(CodeBlock.of("%N[%L]", result, 0))
            else -> encodeBuilder.unflattenResult(CodeBlock.of("%N[%L]", result, 0), encoder)
          }
          val liftedReturnValue = encodeBuilder.lift(
            values = coreReturnValues,
            encoder = encoder,
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
    }

    var argIndex = 0
    val liftedParameterValues = mutableListOf<CodeBlock>()
    val receiverValue = when (receiver) {
      is Receiver.Id -> receiver.codeBlock(CodeBlock.of("%N[%L]", "args", argIndex++))
      is Receiver.Instance -> receiver.codeBlock
    }
    for (coreParameter in coreParameters) {
      val args = coreParameter.encoder.coreTypes.map {
        CodeBlock.of("%N[%L]", "args", argIndex++)
      }
      liftedParameterValues += encodeBuilder.lift(
        values = args,
        encoder = coreParameter.encoder,
      )
    }

    val returnType = value.returnType
    val result = nameAllocator.newName("result")
    val self = nameAllocator.newName("self")
    code.addStatement("val %N = %L", self, receiverValue)
    if (returnType != null) {
      code.add("val %N = ", result)
    }
    code.add("%N.%N(⇥\n", self, value.kotlinName)
    for ((index, parameter) in value.parameters.withIndex()) {
      code.add("%N = %L,\n", nameAllocator[parameter.name], liftedParameterValues[index])
    }
    code.add("⇤)\n")

    val returnValType: CoreType?
    if (returnType != null) {
      val encoder = encoderFactory.get(returnType)
      val loweredReturnValues = encodeBuilder.lower(
        value = CodeBlock.of("%N", result),
        encoder = encoder,
      )
      val flattenedReturnValue = when (encoder.coreTypes.size) {
        1 -> {
          returnValType = encoder.coreTypes.single()
          loweredReturnValues.single()
        }

        else -> {
          returnValType = CoreType.Pointer
          encodeBuilder.flattenResult(loweredReturnValues, encoder)
        }
      }
      code.add(
        "return@%T longArrayOf(%L)",
        Symbols.ChicoryRuntime.WasmFunctionHandle,
        flattenedReturnValue,
      )
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

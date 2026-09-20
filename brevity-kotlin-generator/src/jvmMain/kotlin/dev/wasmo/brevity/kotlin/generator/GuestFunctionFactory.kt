package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.KotlinMapper
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.ParameterList
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Receiver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Creates bridge functions that run on the guest.
 */
internal class GuestFunctionFactory(
  private val kotlinMapper: KotlinMapper,
  guestPlatform: GuestPlatform,
  private val encoderFactory: EncoderFactory,
  private val receiver: Receiver,
  private val value: IrFunction,
  private val supportAsync: Boolean,
) {
  private val used = AtomicBoolean()

  private val nameAllocator = NameAllocator()

  private val function = run {
    val factory = BridgeFunction.Factory(
      receiver = receiver,
      encoderFactory = encoderFactory,
      nameAllocator = nameAllocator,
    )

    factory.create(value)
  }

  private val codeBuilder = CodeBuilder(
    bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
    platform = guestPlatform,
    nameAllocator = nameAllocator,
  )

  /** Bridge an API function into a call to [wasmImport]. */
  fun callHost(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }
    require(receiver !is Receiver.InboundInstance)

    return FunSpec.builder(function.kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        context(codeBuilder) {
          if (value.async && supportAsync) {
            addModifiers(KModifier.SUSPEND)
          }
          val parameterValues = mutableListOf<CodeBlock>()
          for (parameter in value.parameters) {
            addParameter(nameAllocator[parameter.name], kotlinMapper.get(parameter.type))
            parameterValues += CodeBlock.of("%N", nameAllocator[parameter.name])
          }

          val loweredParameters = mutableListOf<CodeBlock>()
          loweredParameters += CodeBlock.of("this.%L", "id")
          when (function.parameterList) {
            is ParameterList.Flattened -> {
              loweredParameters += value.parameters.indices.flatMap { index ->
                function.parameterList.parameters[index].encoder.lowerFlat(
                  value = parameterValues[index],
                )
              }
            }

            is ParameterList.Stored -> {
              codeBuilder.addStatement(
                "val %N = %L",
                function.parameterList.addressSpec.name,
                codeBuilder.allocate("%L", function.parameterList.byteCount),
              )
              val addressParameterValue = CodeBlock.of(
                "%N",
                function.parameterList.addressSpec.name,
              )
              function.parameterList.storeAll(
                baseAddress = addressParameterValue,
                fieldValues = parameterValues,
              )
              loweredParameters += codeBuilder.platform.lowerAddress(addressParameterValue)
            }
          }

          if (function.result != null) {
            when {
              function.result.pointerParameter != null -> {
                codeBuilder.addStatement(
                  "val %N = %L",
                  function.result.pointerParameter.name,
                  codeBuilder.allocate("%L", CodeBlock.of("%L", function.result.encoder.byteCount)),
                )
                loweredParameters += with(codeBuilder) {
                  platform.lowerAddress(CodeBlock.of("%N", function.result.pointerParameter.name))
                }
              }

              else -> {
                codeBuilder.add("val %N = ", function.result.name)
              }
            }
          }
          codeBuilder.add("%N(⇥", value.functionName.importFunctionName)
          if (loweredParameters.isNotEmpty()) {
            codeBuilder.add("\n")
          }
          for (output in loweredParameters) {
            codeBuilder.add("%L,\n", output)
          }
          codeBuilder.add("⇤)\n")

          if (function.result != null) {
            returns(kotlinMapper.get(function.result.type))
            val returnValue = when {
              function.result.pointerParameter != null -> function.result.encoder.load(
                CodeBlock.of("%N", function.result.pointerParameter.name),
              )

              else -> function.result.encoder.liftFlat(
                values = listOf(CodeBlock.of("%N", function.result.name)),
              )
            }
            codeBuilder.add("return %L", returnValue)
            codeBuilder.add(
              "\n⇥.also { %M() }⇤\n",
              Symbols.KotlinWasm.FreeAllComponentModelReallocAllocatedMemory,
            )
          }
        }
      }
      .addCode(codeBuilder.build())
      .build()
  }

  /** Returns the `@WasmImport`-annotated function. It must be added directly to a file. */
  fun wasmImport(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }
    require(receiver !is Receiver.InboundInstance)

    return FunSpec.builder(value.functionName.importFunctionName)
      .addAnnotation(value.functionName.wasmImportAnnotation)
      .addModifiers(KModifier.PRIVATE, KModifier.EXTERNAL)
      .apply {
        if (function.receiver != null) {
          addParameters(function.receiver.coreSpecs)
        }
        when (function.parameterList) {
          is ParameterList.Flattened -> {
            for (coreParameter in function.parameterList.parameters) {
              addParameters(coreParameter.coreSpecs)
            }
          }

          is ParameterList.Stored -> {
            addParameter(function.parameterList.addressSpec)
          }
        }
        if (function.result?.pointerParameter != null) {
          addParameter(function.result.pointerParameter)
        }

        if (function.result != null && function.result.pointerParameter == null) {
          returns(function.result.encoder.coreTypes.single().kotlinCoreType)
        }
      }
      .build()
  }

  /** Returns the `@WasmExport`-annotated function. It must be added directly to a file. */
  fun wasmExport(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }
    require(receiver !is Receiver.OutboundInstance)

    return FunSpec.builder(value.functionName.exportFunctionName)
      .addAnnotation(value.functionName.wasmExportAnnotation)
      .addModifiers(KModifier.PRIVATE)
      .apply {
        context(codeBuilder) {
          val liftedReceiver = when {
            function.receiver != null -> {
              addParameters(function.receiver.coreSpecs)

              function.receiver.encoder.liftFlat(
                values = function.receiver.coreSpecs.map { CodeBlock.of("%N", it) },
              )
            }

            else -> (receiver as Receiver.InboundInstance).codeBlock
          }

          when (function.parameterList) {
            is ParameterList.Flattened -> {
              for ((index, coreParameter) in function.parameterList.parameters.withIndex()) {
                addParameters(coreParameter.coreSpecs)
                val liftedParameterExpression = coreParameter.encoder.liftFlat(
                  values = coreParameter.coreSpecs.map { CodeBlock.of("%N", it) },
                )
                codeBuilder.addStatement(
                  "val %N = %L",
                  nameAllocator[value.parameters[index].name],
                  liftedParameterExpression,
                )
              }
            }

            is ParameterList.Stored -> {
              addParameter(function.parameterList.addressSpec)
              val addressExpression = CodeBlock.of("%L", function.parameterList.addressSpec.name)
              addStatement(
                "val %L = %L",
                function.parameterList.addressSpec.name,
                codeBuilder.platform.liftAddress(addressExpression),
              )
              val loadedParameterExpression = function.parameterList.loadAll(
                baseAddress = addressExpression,
              )
              for ((index, parameter) in value.parameters.withIndex()) {
                codeBuilder.addStatement(
                  "val %N = %L",
                  nameAllocator[parameter.name],
                  loadedParameterExpression[index],
                )
              }
            }
          }

          codeBuilder.addStatement(
            "%M()",
            Symbols.KotlinWasm.FreeAllComponentModelReallocAllocatedMemory,
          )

          if (function.result != null) {
            codeBuilder.add("val %N = ", function.result.name)
          }
          codeBuilder.add("%L.%N(⇥\n", liftedReceiver, function.kotlinName)
          for (parameter in value.parameters) {
            codeBuilder.add(
              "%N = %L,\n",
              nameAllocator[parameter.name],
              nameAllocator[parameter.name],
            )
          }
          codeBuilder.add("⇤)\n")

          codeBuilder.permitAllocationsNow()

          if (function.result != null) {
            when (function.result.encoder.coreTypes.size) {
              1 -> {
                val loweredReturnValues = function.result.encoder.lowerFlat(
                  value = CodeBlock.of("%N", function.result.name),
                )
                returns(function.result.encoder.coreTypes.single().kotlinCoreType)
                codeBuilder.add("return %L\n", loweredReturnValues.single())
              }

              else -> {
                returns(CoreType.Pointer.kotlinCoreType)
                val address = nameAllocator.newName("resultAddress")
                codeBuilder.addStatement(
                  "val %N = %L",
                  address,
                  codeBuilder.allocate("%L", function.result.encoder.byteCount),
                )
                function.result.encoder.store(
                  baseAddress = CodeBlock.of("%N", address),
                  value = CodeBlock.of("%N", function.result.name),
                )
                codeBuilder.add(
                  "return %L\n",
                  codeBuilder.platform.lowerAddress(CodeBlock.of("%N", address)),
                )
              }
            }
          }
        }
      }
      .addCode(codeBuilder.build())
      .build()
  }

  fun callWasmExportFunctionWithPlaceholders(): CodeBlock {
    val receiverAndParameters = buildList {
      if (function.receiver != null) {
        addAll(function.receiver.coreSpecs)
      }
      when (function.parameterList) {
        is ParameterList.Flattened -> {
          for (parameter in function.parameterList.parameters) {
            addAll(parameter.coreSpecs)
          }
        }

        is ParameterList.Stored -> {
          add(function.parameterList.addressSpec)
        }
      }
    }

    return receiverAndParameters.joinToCode(
      prefix = "${value.functionName.exportFunctionName}(",
      suffix = ")",
      transform = { it.placeholder },
    )
  }

  private val ParameterSpec.placeholder: CodeBlock
    get() = when (type) {
      INT -> CodeBlock.of("%L", 0)
      LONG -> CodeBlock.of("%LL", 0)
      FLOAT -> CodeBlock.of("%Lf", 0.0)
      DOUBLE -> CodeBlock.of("%L", 0.0)
      else -> error("unexpected core parameter type")
    }
}

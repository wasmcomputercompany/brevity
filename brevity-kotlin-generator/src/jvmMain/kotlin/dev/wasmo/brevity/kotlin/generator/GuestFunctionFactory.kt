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
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.LoweredParameters
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Receiver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Creates bridge functions that run on the guest.
 */
internal class GuestFunctionFactory(
  private val kotlinMapper: KotlinMapper,
  guestPlatform: GuestPlatform,
  private val function: BridgeFunction,
  private val supportAsync: Boolean,
) {
  private val used = AtomicBoolean()

  private val value: IrFunction
    get() = function.function

  private val nameAllocator: NameAllocator
    get() = function.nameAllocator

  private val receiver: Receiver
    get() = function.liftedReceiver

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
          addParameters(function.liftedParameters)

          val loweredParameters = mutableListOf<Pair<CodeBlock, CoreType>>()
          loweredParameters += function.lowerParameterValues()

          if (
            function.result is BridgeFunction.Result.PointerReturn ||
            function.result is BridgeFunction.Result.SingleCoreValueReturn
          ) {
            codeBuilder.add("val %N = ", function.result.name)
          }

          codeBuilder.add("%N(⇥", value.functionName.importFunctionName)
          if (loweredParameters.isNotEmpty()) {
            codeBuilder.add("\n")
          }
          for ((loweredParameter, _) in loweredParameters) {
            codeBuilder.add("%L,\n", loweredParameter)
          }
          codeBuilder.add("⇤)\n")

          if (function.result != null) {
            returns(kotlinMapper.get(function.result.type))
            val returnValue = when (function.result) {
              is BridgeFunction.Result.PointerParameter -> {
                function.result.encoder.load(
                  CodeBlock.of("%N", function.result.pointerParameter.name),
                )
              }

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
        if (function.loweredReceiver != null) {
          addParameters(function.loweredReceiver.coreSpecs)
        }

        when (function.loweredParameters) {
          is LoweredParameters.Flattened -> {
            for (coreParameter in function.loweredParameters.parameters) {
              addParameters(coreParameter.coreSpecs)
            }
          }

          is LoweredParameters.Stored -> {
            addParameter(function.loweredParameters.addressSpec)
          }
        }

        when (function.result) {
          is BridgeFunction.Result.PointerParameter -> {
            addParameter(function.result.pointerParameter)
          }

          is BridgeFunction.Result.PointerReturn,
          is BridgeFunction.Result.SingleCoreValueReturn,
            -> {
            returns(function.result.encoder.coreTypes.single().kotlinCoreType)
          }

          null -> {}
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
            function.loweredReceiver != null -> {
              addParameters(function.loweredReceiver.coreSpecs)

              function.loweredReceiver.encoder.liftFlat(
                values = function.loweredReceiver.coreSpecs.map { CodeBlock.of("%N", it) },
              )
            }

            else -> (receiver as Receiver.InboundInstance).codeBlock
          }

          when (function.loweredParameters) {
            is LoweredParameters.Flattened -> {
              for ((index, coreParameter) in function.loweredParameters.parameters.withIndex()) {
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

            is LoweredParameters.Stored -> {
              addParameter(function.loweredParameters.addressSpec)
              val addressExpression = CodeBlock.of(
                "%L",
                function.loweredParameters.addressSpec.name,
              )
              addStatement(
                "val %L = %L",
                function.loweredParameters.addressSpec.name,
                codeBuilder.platform.liftAddress(addressExpression),
              )
              val loadedParameterExpression = function.loweredParameters.loadAll(
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

          when (function.result) {
            is BridgeFunction.Result.PointerReturn -> {
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

            is BridgeFunction.Result.SingleCoreValueReturn -> {
              returns(function.result.encoder.coreTypes.single().kotlinCoreType)
              val loweredReturnValues = function.result.encoder.lowerFlat(
                value = CodeBlock.of("%N", function.result.name),
              )
              codeBuilder.add("return %L\n", loweredReturnValues.single())
            }

            is BridgeFunction.Result.PointerParameter -> {
              error("unimplemented")
            }

            null -> {}
          }
        }
      }
      .addCode(codeBuilder.build())
      .build()
  }

  fun callWasmExportFunctionWithPlaceholders(): CodeBlock {
    val receiverAndParameters = buildList {
      if (function.loweredReceiver != null) {
        addAll(function.loweredReceiver.coreSpecs)
      }
      when (function.loweredParameters) {
        is LoweredParameters.Flattened -> {
          for (parameter in function.loweredParameters.parameters) {
            addAll(parameter.coreSpecs)
          }
        }

        is LoweredParameters.Stored -> {
          add(function.loweredParameters.addressSpec)
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

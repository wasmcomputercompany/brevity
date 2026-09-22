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
          addParameters(function.liftedParameterSpecs)

          val loweredParameterValues = function.lowerParameterValues()

          if (function.result != null) {
            codeBuilder.add("val %N = ", function.result.name)
          }
          codeBuilder.add("%N(⇥", value.functionName.importFunctionName)
          if (loweredParameterValues.isNotEmpty()) {
            codeBuilder.add("\n")
          }
          for (loweredParameterValue in loweredParameterValues) {
            codeBuilder.add("%L,\n", loweredParameterValue)
          }
          codeBuilder.add("⇤)\n")

          if (function.result != null) {
            returns(kotlinMapper.get(function.result.type))
          }

          val returnValue = function.liftReturnValue { name, _ ->
            CodeBlock.of("%N", name)
          }

          if (returnValue != null) {
            codeBuilder.add("return %L", returnValue)
            codeBuilder.add(
              "\n⇥.also { %M() }⇤\n",
              Symbols.KotlinWasm.FreeAllComponentModelReallocAllocatedMemory,
            )
          } else {
            codeBuilder.addStatement(
              "%M()",
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
        addParameters(function.loweredParameterSpecs)

        when (function.result) {
          is BridgeFunction.Result.PointerReturn,
          is BridgeFunction.Result.SingleCoreValueReturn,
            -> {
            returns(function.result.encoder.coreTypes.single().kotlinCoreType)
          }

          else -> {}
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
          val loweredParameterSpecs = function.loweredParameterSpecs
          addParameters(loweredParameterSpecs)

          val loweredParameterValues = loweredParameterSpecs.map {
            CodeBlock.of("%N", it)
          }

          val liftedParameterValues = function.liftParameterValues(
            loweredParameterValues,
          )

          codeBuilder.addStatement(
            "%M()",
            Symbols.KotlinWasm.FreeAllComponentModelReallocAllocatedMemory,
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

          codeBuilder.permitAllocationsNow()

          val returnValue = function.lowerReturnValue(liftedParameterValues)
          if (returnValue != null) {
            returns(function.loweredReturnType!!.kotlinCoreType)
            codeBuilder.add(
              "return %L\n",
              returnValue,
            )
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

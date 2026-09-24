package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.joinToCode
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Invoker
import dev.wasmo.brevity.kotlin.generator.BridgeFunction.Receiver
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Creates bridge functions that run on the guest.
 */
internal class GuestFunctionFactory(
  guestPlatform: GuestPlatform,
  private val function: BridgeFunction,
) {
  private val used = AtomicBoolean()

  private val codeBuilder = CodeBuilder(
    bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
    platform = guestPlatform,
    nameAllocator = function.nameAllocator,
  )

  /** Bridge an API function into a call to [wasmImport]. */
  fun callHost(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    val invoker = object : Invoker {
      context(codeBuilder: CodeBuilder)
      override fun invoke(parameterValues: List<CodeBlock>) {
        if (function.result != null) {
          codeBuilder.add("val %N = ", function.result.loweredName)
        }
        codeBuilder.add("%N(⇥", function.functionName.importFunctionName)
        if (parameterValues.isNotEmpty()) {
          codeBuilder.add("\n")
        }
        for (loweredParameterValue in parameterValues) {
          codeBuilder.add("%L,\n", loweredParameterValue)
        }
        codeBuilder.add("⇤)\n")
      }
    }

    return function.outboundFunction(codeBuilder, invoker)
  }

  /** Returns the `@WasmImport`-annotated function. It must be added directly to a file. */
  fun wasmImport(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }
    require(function.liftedReceiver !is Receiver.InboundInstance)

    return FunSpec.builder(function.functionName.importFunctionName)
      .addAnnotation(function.functionName.wasmImportAnnotation)
      .addModifiers(KModifier.PRIVATE, KModifier.EXTERNAL)
      .apply {
        addParameters(function.loweredParameterSpecs)
        val loweredReturnType = function.loweredReturnType
        if (loweredReturnType != null) {
          returns(loweredReturnType.kotlinCoreType)
        }
      }
      .build()
  }

  /** Returns the `@WasmExport`-annotated function. It must be added directly to a file. */
  fun wasmExport(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }
    require(function.liftedReceiver !is Receiver.OutboundInstance)

    return FunSpec.builder(function.functionName.exportFunctionName)
      .addAnnotation(function.functionName.wasmExportAnnotation)
      .addModifiers(KModifier.PRIVATE)
      .apply {
        context(codeBuilder) {
          val loweredParameterSpecs = function.loweredParameterSpecs
          addParameters(loweredParameterSpecs)

          val returnValue = function.liftInboundCall(
            parameterValues = loweredParameterSpecs.map {
              CodeBlock.of("%N", it)
            },
          )

          if (returnValue != null) {
            returns(function.loweredReturnType!!.kotlinCoreType)
            codeBuilder.addStatement("return %L", returnValue)
          }
        }
      }
      .addCode(codeBuilder.build())
      .build()
  }

  fun callWasmExportFunctionWithPlaceholders(): CodeBlock {
    return function.loweredParameterSpecs.joinToCode(
      prefix = "${function.functionName.exportFunctionName}(",
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

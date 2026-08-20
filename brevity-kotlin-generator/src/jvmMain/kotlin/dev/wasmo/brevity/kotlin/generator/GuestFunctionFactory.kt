package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.GuestPlatform
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Creates bridge functions that run on the guest.
 */
internal class GuestFunctionFactory(
  private val encoderFactory: EncoderFactory,
  private val receiver: Receiver,
  private val value: IrFunction,
) {
  private val used = AtomicBoolean()

  private val nameAllocator = NameAllocator().apply {
    // Pre-allocate the names we'll need.
    for (parameter in value.parameters) {
      newName(parameter.kotlinName, parameter.name)
    }
    if (receiver is Receiver.Id) {
      newName(receiver.name.lowerCamelCase, receiver.name)
    }
  }

  private val coreValueFactory = CoreValueFactory(
    encoderFactory = encoderFactory,
    nameAllocator = nameAllocator,
  )

  private val coreReceiver: CoreParameter? = when {
    receiver is Receiver.Id -> coreValueFactory.parameter(receiver.name, receiver.type)
    else -> null
  }
  private val coreParameters = value.parameters.map { coreValueFactory.parameter(it.name, it.type) }
  private val coreResult = value.returnType?.let { coreValueFactory.result(it) }

  private val codeBuilder = CodeBuilder(
    bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
    platform = GuestPlatform,
    nameAllocator = nameAllocator,
  )

  /** Bridge an API function into a call to [wasmImport]. */
  fun callHost(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    return FunSpec.builder(value.kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        context(codeBuilder) {
          val loweredParameters = mutableListOf<CodeBlock>()
          loweredParameters += CodeBlock.of("this.%L", "id")

          for ((index, parameter) in value.parameters.withIndex()) {
            addParameter(nameAllocator[parameter.name], parameter.type.kotlinApi)
            loweredParameters += coreParameters[index].encoder.lowerFlat(
              value = CodeBlock.of("%N", nameAllocator[parameter.name]),
            )
          }

          if (coreResult != null) {
            when {
              coreResult.parameter != null -> {
                codeBuilder.addStatement(
                  "val %N = %L",
                  coreResult.parameter.name,
                  codeBuilder.allocate("%L", CodeBlock.of("%L", coreResult.encoder.byteCount)),
                )
                loweredParameters += with(codeBuilder) {
                  platform.lowerAddress(CodeBlock.of("%N", coreResult.parameter.name))
                }
              }

              else -> {
                codeBuilder.add("val %N = ", coreResult.name)
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

          if (coreResult != null) {
            returns(coreResult.type.kotlinApi)
            val returnValue = when {
              coreResult.parameter != null -> coreResult.encoder.load(
                CodeBlock.of("%N", coreResult.parameter.name),
              )
              else -> coreResult.encoder.liftFlat(
                values = listOf(CodeBlock.of("%N", coreResult.name)),
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

    return FunSpec.builder(value.functionName.importFunctionName)
      .addAnnotation(value.functionName.wasmImportAnnotation)
      .addModifiers(KModifier.PRIVATE, KModifier.EXTERNAL)
      .apply {
        if (coreReceiver != null) {
          addParameters(coreReceiver.specs)
        }
        for (coreParameter in coreParameters) {
          addParameters(coreParameter.specs)
        }
        if (coreResult?.parameter != null) {
          addParameter(coreResult.parameter)
        }

        if (coreResult != null && coreResult.parameter == null) {
          returns(coreResult.encoder.coreTypes.single().kotlinCoreType)
        }
      }
      .build()
  }

  /** Returns the `@WasmExport`-annotated function. It must be added directly to a file. */
  fun wasmExport(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    return FunSpec.builder(value.functionName.exportFunctionName)
      .addAnnotation(value.functionName.wasmExportAnnotation)
      .addModifiers(KModifier.PRIVATE)
      .apply {
        context(codeBuilder) {
          val liftedReceiver = when (receiver) {
            is Receiver.Id -> {
              addParameters(coreReceiver!!.specs)

              coreReceiver.encoder.liftFlat(
                values = coreReceiver.names.map { CodeBlock.of("%N", it) },
              )
            }

            is Receiver.Global -> receiver.codeBlock
          }

          val liftedParameterValues = mutableListOf<CodeBlock>()
          for (coreParameter in coreParameters) {
            addParameters(coreParameter.specs)
            liftedParameterValues += coreParameter.encoder.liftFlat(
              values = coreParameter.names.map { CodeBlock.of("%N", it) },
            )
          }

          if (coreResult != null) {
            codeBuilder.add("val %N = ", coreResult.name)
          }
          codeBuilder.add("%L.%N(⇥\n", liftedReceiver, value.kotlinName)
          for ((index, parameter) in value.parameters.withIndex()) {
            codeBuilder.add(
              "%N = %L,\n",
              nameAllocator[parameter.name],
              liftedParameterValues[index],
            )
          }
          codeBuilder.add("⇤)\n")

          if (coreResult != null) {
            when (coreResult.encoder.coreTypes.size) {
              1 -> {
                val loweredReturnValues = coreResult.encoder.lowerFlat(
                  value = CodeBlock.of("%N", coreResult.name),
                )
                returns(coreResult.encoder.coreTypes.single().kotlinCoreType)
                codeBuilder.add("return %L\n", loweredReturnValues.single())
              }

              else -> {
                returns(CoreType.Pointer.kotlinCoreType)
                val address = nameAllocator.newName("resultAddress")
                codeBuilder.addStatement(
                  "val %N = %L",
                  address,
                  codeBuilder.allocate("%L", coreResult.encoder.byteCount),
                )
                coreResult.encoder.store(
                  baseAddress = CodeBlock.of("%N", address),
                  value = CodeBlock.of("%N", coreResult.name),
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

  internal sealed interface Receiver {
    data class Global(
      val codeBlock: CodeBlock,
    ) : Receiver

    data class Id(
      val type: TypeName,
    ) : Receiver {
      val name: Identifier
        get() = Identifier("self")
    }
  }
}

package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.GuestPlatform
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
      newName(receiver.name.toCamelCase(upperCamel = false), receiver.name)
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

  private val code = CodeBlock.Builder()

  private val encodeBuilder = RealEncodeBuilder(
    bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
    nameAllocator = nameAllocator,
    code = code,
    platform = GuestPlatform,
  )

  /** Bridge an API function into a call to [wasmImport]. */
  fun callHost(): FunSpec {
    require(used.compareAndSet(false, true)) { "cannot be reused" }

    return FunSpec.builder(value.kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        val loweredParameters = mutableListOf<CodeBlock>()
        loweredParameters += CodeBlock.of("this.%L", "id")

        for ((index, parameter) in value.parameters.withIndex()) {
          addParameter(nameAllocator[parameter.name], parameter.type.kotlinApi)
          loweredParameters += encodeBuilder.lower(
            value = CodeBlock.of("%N", nameAllocator[parameter.name]),
            encoder = coreParameters[index].encoder,
          )
        }

        if (coreResult != null) {
          when {
            coreResult.parameter != null -> {
              encodeBuilder.allocate(coreResult, coreResult.parameter.name)
              loweredParameters += with(encodeBuilder) {
                platform.lowerAddress(CodeBlock.of("%N", coreResult.parameter.name))
              }
            }

            else -> {
              code.add("val %N = ", coreResult.name)
            }
          }
        }
        code.add("%N(⇥", value.functionName.importFunctionName)
        if (loweredParameters.isNotEmpty()) {
          code.add("\n")
        }
        for (output in loweredParameters) {
          code.add("%L,\n", output)
        }
        code.add("⇤)\n")

        if (coreResult != null) {
          returns(coreResult.type.kotlinApi)
          val coreReturnValues = when {
            coreResult.parameter != null -> {
              encodeBuilder.loadResultFromMemory(coreResult.parameter.name, coreResult)
            }

            else -> {
              listOf(CodeBlock.of("%N", coreResult.name))
            }
          }
          val liftedReturnValue = encodeBuilder.lift(
            values = coreReturnValues,
            encoder = coreResult.encoder,
          )
          code.add("return %L", liftedReturnValue)
          code.add(
            "\n⇥.also { %M() }⇤\n",
            Symbols.KotlinWasm.FreeAllComponentModelReallocAllocatedMemory,
          )
        }
      }
      .addCode(buildCodeBlock())
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
        val liftedReceiver = when (receiver) {
          is Receiver.Id -> {
            addParameters(coreReceiver!!.specs)
            encodeBuilder.lift(
              values = coreReceiver.names.map { CodeBlock.of("%N", it) },
              encoder = coreReceiver.encoder,
            )
          }

          is Receiver.Global -> receiver.codeBlock
        }

        val liftedParameterValues = mutableListOf<CodeBlock>()
        for (coreParameter in coreParameters) {
          addParameters(coreParameter.specs)
          liftedParameterValues += encodeBuilder.lift(
            values = coreParameter.names.map { CodeBlock.of("%N", it) },
            encoder = coreParameter.encoder,
          )
        }

        if (coreResult != null) {
          code.add("val %N = ", coreResult.name)
        }
        code.add("%L.%N(⇥\n", liftedReceiver, value.kotlinName)
        for ((index, parameter) in value.parameters.withIndex()) {
          code.add("%N = %L,\n", nameAllocator[parameter.name], liftedParameterValues[index])
        }
        code.add("⇤)\n")

        if (coreResult != null) {
          val loweredReturnValues = encodeBuilder.lower(
            value = CodeBlock.of("%N", coreResult.name),
            encoder = coreResult.encoder,
          )
          val flattenedReturnValue = when (coreResult.encoder.coreTypes.size) {
            1 -> {
              returns(coreResult.encoder.coreTypes.single().kotlinCoreType)
              loweredReturnValues.single()
            }

            else -> {
              returns(CoreType.Pointer.kotlinCoreType)
              encodeBuilder.flattenResult(loweredReturnValues, coreResult)
            }
          }
          code.add("return %L\n", flattenedReturnValue)
        }
      }
      .addCode(buildCodeBlock())
      .build()
  }

  private fun buildCodeBlock(): CodeBlock {
    val memoryAllocator = encodeBuilder.memoryAllocator
    return when {
      memoryAllocator != null -> com.squareup.kotlinpoet.buildCodeBlock {
        beginControlFlow(
          "%M { %N ->",
          Symbols.KotlinWasm.WithScopedMemoryAllocator,
          memoryAllocator,
        )
        add(code.build())
        endControlFlow()
      }

      else -> code.build()
    }
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


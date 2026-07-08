package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory

internal class GuestBridgeBuilder(
  private val encoderFactory: EncoderFactory,
) {
  /** Returns a function that calls the host. It implements the friendly API. */
  fun callHostFunction(
    receiver: Receiver.Id,
    value: IrFunction,
  ): FunSpec {
    return FunSpec.builder(value.kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .returns(value.returnType?.kotlinApi ?: UNIT)
      .apply {
        val returnType = value.returnType
        if (returnType != null) {
          addCode("val %N = ", "result")
        }

        addCode("%N(⇥\n", value.functionName.importFunctionName)
        addCode("%N = %N,\n", receiver.name, "id")

        for (parameter in value.parameters) {
          val encoder = encoderFactory.get(typeName = parameter.type)
          addParameter(parameter.kotlinName, parameter.type.kotlinApi)
          addCode(
            "%N = %L,\n",
            parameter.kotlinName,
            encoder.valueToCoreType(
              bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
              value = CodeBlock.of("%N", parameter.kotlinName),
            ),
          )
        }

        addCode("⇤)\n")

        if (returnType != null) {
          val encoder = encoderFactory.get(typeName = returnType)
          addCode(
            "return %L",
            encoder.coreTypeToValue(
              bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
              coreType = CodeBlock.of("%N", "result"),
            ),
          )
        }
      }
      .build()
  }

  /** Returns the `@WasmImport`-annotated function. It must be added directly to a file. */
  fun wasmImportFunction(
    receiver: Receiver,
    value: IrFunction,
  ): FunSpec {
    return FunSpec.builder(value.functionName.importFunctionName)
      .addAnnotation(
        AnnotationSpec.builder(Symbols.KotlinWasm.WasmImport)
          .apply {
            val moduleName = value.functionName.moduleName
            if (moduleName != null) {
              addMember("module = %S", moduleName)
              addMember("name = %S", value.functionName.abiName)
            } else {
              addMember("module = %S", value.functionName.abiName)
            }
          }
          .build(),
      )
      .addModifiers(KModifier.PRIVATE, KModifier.EXTERNAL)
      .apply {
        if (receiver is Receiver.Id) {
          val encoder = encoderFactory.get(receiver.type)
          addParameter(receiver.name, encoder.coreType.kotlinCoreType)
        }
        for (parameter in value.parameters) {
          val encoder = encoderFactory.get(parameter.type)
          addParameter(parameter.kotlinName, encoder.coreType.kotlinCoreType)
        }
        val returnType = value.returnType
        if (returnType != null) {
          val encoder = encoderFactory.get(returnType)
          returns(encoder.coreType.kotlinCoreType)
        }
      }
      .build()
  }

  /** Returns the `@WasmExport`-annotated function. It must be added directly to a file. */
  fun wasmExportFunction(
    receiver: Receiver,
    value: IrFunction,
  ): FunSpec {
    return FunSpec.builder(value.functionName.exportFunctionName)
      .addAnnotation(
        AnnotationSpec.builder(Symbols.KotlinWasm.WasmExport)
          .addMember("%S", value.functionName)
          .build(),
      )
      .addModifiers(KModifier.PRIVATE)
      .apply {
        val returnType = value.returnType
        if (returnType != null) {
          val encoder = encoderFactory.get(returnType)
          addCode("val %N = ", "result")
          returns(encoder.coreType.kotlinCoreType)
        }

        val guestBridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge)

        when (receiver) {
          is Receiver.Id -> {
            val encoder = encoderFactory.get(receiver.type)
            addParameter(receiver.name, encoder.coreType.kotlinCoreType)
            addCode(encoder.coreTypeToValue(guestBridge, CodeBlock.of("%N", receiver.name)))
          }

          is Receiver.Global -> {
            addCode(receiver.codeBlock)
          }
        }

        addCode(".%N(⇥\n", value.kotlinName)
        for (parameter in value.parameters) {
          val encoder = encoderFactory.get(typeName = parameter.type)
          addParameter(parameter.kotlinName, encoder.coreType.kotlinCoreType)
          addCode(
            "%N = %L,\n",
            parameter.kotlinName,
            encoder.coreTypeToValue(
              bridge = guestBridge,
              coreType = CodeBlock.of("%N", parameter.kotlinName),
            ),
          )
        }
        addCode("⇤)\n")

        if (returnType != null) {
          val encoder = encoderFactory.get(typeName = returnType)
          addCode(
            "return %L\n",
            encoder.valueToCoreType(
              bridge = guestBridge,
              value = CodeBlock.of("%N", "result"),
            ),
          )
        }
      }
      .build()
  }

  internal sealed interface Receiver {
    data class Global(
      val codeBlock: CodeBlock,
    ) : Receiver

    data class Id(
      val type: TypeName,
    ) : Receiver {
      val name: String
        get() = "self"
    }
  }
}

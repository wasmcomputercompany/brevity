package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.ir.IrResource

internal class GuestBridgeBuilder(
  private val index: DeclarationIndex,
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
          addParameter(parameter.kotlinName, parameter.type.kotlinApi)
          addCode(
            "%N = %L,\n",
            parameter.kotlinName,
            guestApiToAbi(
              bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
              type = parameter.type,
              abiValue = CodeBlock.of("%N", parameter.kotlinName),
            ),
          )
        }

        addCode("⇤)\n")

        if (returnType != null) {
          addCode(
            "return %L",
            guestAbiToApi(
              bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
              type = returnType,
              abiValue = CodeBlock.of("%N", "result"),
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
          addParameter(receiver.name, receiver.type.kotlinAbi)
        }
        for (parameter in value.parameters) {
          addParameter(parameter.kotlinName, parameter.type.kotlinAbi)
        }
      }
      .returns(value.returnType?.kotlinAbi ?: UNIT)
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
      .returns(value.returnType?.kotlinAbi ?: UNIT)
      .apply {
        if (receiver is Receiver.Id) {
          addParameter(receiver.name, receiver.type.kotlinAbi)
        }

        val returnType = value.returnType
        if (returnType != null) {
          addCode("val %N = ", "result")
        }

        addCode("%L", receiver.codeBlock)

        val guestBridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge)
        addCode(".%N(⇥\n", value.kotlinName)
        for (parameter in value.parameters) {
          addParameter(parameter.kotlinName, parameter.type.kotlinAbi)
          addCode(
            "%N = %L,\n",
            parameter.kotlinName,
            guestAbiToApi(
              bridge = guestBridge,
              type = parameter.type,
              abiValue = CodeBlock.of("%N", parameter.kotlinName),
            ),
          )
        }
        addCode("⇤)\n")

        if (returnType != null) {
          addCode(
            "return %L\n",
            guestApiToAbi(
              bridge = guestBridge,
              type = returnType,
              abiValue = CodeBlock.of("%N", "result"),
            ),
          )
        }
      }
      .build()
  }

  /** Lift an ABI value like a memory address to an API value like a resource instance. */
  fun guestAbiToApi(
    bridge: CodeBlock,
    type: TypeName,
    abiValue: CodeBlock,
  ): CodeBlock {
    return when (type) {
      is TypeName.Declared -> {
        when (index[type]) {
          is IrResource -> CodeBlock.of(
            "%L.fromId(%L, ::%T)",
            bridge,
            abiValue,
            type.handleName,
          )

          else -> CodeBlock.of(
            "%L as %T",
            abiValue,
            type.kotlinApi,
          )
        }
      }

      else -> CodeBlock.of(
        "%L as %T",
        abiValue,
        type.kotlinApi,
      )
    }
  }

  /** Lower an API value like a resource instance to an ABI value like a memory address. */
  fun guestApiToAbi(
    bridge: CodeBlock,
    type: TypeName,
    abiValue: CodeBlock,
  ): CodeBlock {
    return when (type) {
      else -> CodeBlock.of(
        "%L as %T",
        abiValue,
        type.kotlinAbi,
      )
    }
  }

  internal sealed interface Receiver {
    val codeBlock: CodeBlock

    data class Global(
      override val codeBlock: CodeBlock,
    ) : Receiver

    data class Id(
      val type: TypeName,
    ) : Receiver {
      val name: String
        get() = "self"

      override val codeBlock: CodeBlock
        get() = CodeBlock.of(
          "%T.fromId<%T>(%N, ::%T)",
          Symbols.Brevity.GuestBridge,
          type.kotlinApi,
          "self",
          type.kotlinApi,
        )
    }
  }
}

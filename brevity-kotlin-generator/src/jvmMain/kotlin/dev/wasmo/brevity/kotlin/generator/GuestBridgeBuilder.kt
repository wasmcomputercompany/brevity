package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.ir.IrResource

internal class GuestBridgeBuilder(
  private val index: DeclarationIndex,
) {
  /** Returns a function that calls the host. It implements the friendly API. */
  fun callHostFunction(
    receiver: Receiver.Id,
    value: KtFunction,
  ): FunSpec {
    return FunSpec.builder(value.ktName)
      .addModifiers(KModifier.OVERRIDE)
      .returns(value.returnType?.apiType ?: UNIT)
      .apply {
        if (value.returnType != null) {
          addCode("val %N = ", "result")
        }

        addCode("%N(⇥\n", value.name.importFunctionName)
        addCode("%N = %N,\n", receiver.name, "id")

        for (parameter in value.parameters) {
          addParameter(parameter.name, parameter.type.apiType)
          addCode(
            "%N = %L,\n",
            parameter.name,
            guestApiToAbi(
              bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
              type = parameter.type,
              abiValue = CodeBlock.of("%N", parameter.name),
            ),
          )
        }

        addCode("⇤)\n")

        if (value.returnType != null) {
          addCode(
            "return %L",
            guestAbiToApi(
              bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
              type = value.returnType,
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
    value: KtFunction,
  ): FunSpec {
    return FunSpec.builder(value.name.importFunctionName)
      .addAnnotation(
        AnnotationSpec.builder(Symbols.KotlinWasm.WasmImport)
          .apply {
            val moduleName = value.name.moduleName
            if (moduleName != null) {
              addMember("module = %S", moduleName)
              addMember("name = %S", value.name.abiName)
            } else {
              addMember("module = %S", value.name.abiName)
            }
          }
          .build(),
      )
      .addModifiers(KModifier.PRIVATE, KModifier.EXTERNAL)
      .apply {
        if (receiver is Receiver.Id) {
          addParameter(receiver.name, receiver.type.abiType)
        }
        for (parameter in value.parameters) {
          addParameter(parameter.name, parameter.type.abiType)
        }
      }
      .returns(value.returnType?.abiType ?: UNIT)
      .build()
  }

  /** Returns the `@WasmExport`-annotated function. It must be added directly to a file. */
  fun wasmExportFunction(
    receiver: Receiver,
    value: KtFunction,
  ): FunSpec {
    return FunSpec.builder(value.name.exportFunctionName)
      .addAnnotation(
        AnnotationSpec.builder(Symbols.KotlinWasm.WasmExport)
          .addMember("%S", value.name)
          .build(),
      )
      .addModifiers(KModifier.PRIVATE)
      .returns(value.returnType?.abiType ?: UNIT)
      .apply {
        if (receiver is Receiver.Id) {
          addParameter(receiver.name, receiver.type.abiType)
        }

        if (value.returnType != null) {
          addCode("val %N = ", "result")
        }

        addCode("%L", receiver.codeBlock)

        val guestBridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge)
        addCode(".%N(⇥\n", value.ktName)
        for (parameter in value.parameters) {
          addParameter(parameter.name, parameter.type.abiType)
          addCode(
            "%N = %L,\n",
            parameter.name,
            guestAbiToApi(
              bridge = guestBridge,
              type = parameter.type,
              abiValue = CodeBlock.of("%N", parameter.name),
            ),
          )
        }
        addCode("⇤)\n")

        if (value.returnType != null) {
          addCode(
            "return %L\n",
            guestApiToAbi(
              bridge = guestBridge,
              type = value.returnType,
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
    type: KtTypeName,
    abiValue: CodeBlock,
  ): CodeBlock {
    return when (type) {
      is KtTypeName.Declared -> {
        when (index[type.witType]) {
          is IrResource -> CodeBlock.of(
            "%L.fromId(%L, ::%T)",
            bridge,
            abiValue,
            type.handleName,
          )

          else -> CodeBlock.of(
            "%L as %T",
            abiValue,
            type.apiType,
          )
        }
      }

      else -> CodeBlock.of(
        "%L as %T",
        abiValue,
        type.apiType,
      )
    }
  }

  /** Lower an API value like a resource instance to an ABI value like a memory address. */
  fun guestApiToAbi(
    bridge: CodeBlock,
    type: KtTypeName,
    abiValue: CodeBlock,
  ): CodeBlock {
    return when (type) {
      else -> CodeBlock.of(
        "%L as %T",
        abiValue,
        type.abiType,
      )
    }
  }

  internal sealed interface Receiver {
    val codeBlock: CodeBlock

    data class Global(
      override val codeBlock: CodeBlock,
    ) : Receiver

    data class Id(
      val type: KtTypeName,
    ) : Receiver {
      val name: String
        get() = "self"

      override val codeBlock: CodeBlock
        get() = CodeBlock.of(
          "%T.fromId<%T>(%N, ::%T)",
          Symbols.Brevity.GuestBridge,
          type.apiType,
          "self",
          type.apiType,
        )
    }
  }
}

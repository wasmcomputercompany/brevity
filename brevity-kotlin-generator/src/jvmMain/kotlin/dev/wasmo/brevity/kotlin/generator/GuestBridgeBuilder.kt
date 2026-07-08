package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.GuestEncodeBuilder

internal class GuestBridgeBuilder(
  private val encoderFactory: EncoderFactory,
) {
  /** Returns a function that calls the host. It implements the friendly API. */
  fun callHostFunction(
    receiver: Receiver.Id,
    value: IrFunction,
  ): FunSpec {
    val returnType = value.returnType
    val encodeBuilder = GuestEncodeBuilder(
      bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
    )

    val parameterCodeBlocks = mutableListOf<CodeBlock>()
    parameterCodeBlocks += CodeBlock.of("this.%L", "id")

    for (parameter in value.parameters) {
      parameterCodeBlocks += encodeBuilder.lower(
        value = CodeBlock.of("%N", parameter.kotlinName),
        encoder = encoderFactory.get(typeName = parameter.type),
      ) {
        encodeBuilder.valueToCoreType()
      }
    }

    if (returnType != null) {
      encodeBuilder.code.add("val %N = ", "result")
    }
    encodeBuilder.code.add("%N(⇥\n", value.functionName.importFunctionName)
    for (output in parameterCodeBlocks) {
      encodeBuilder.code.add("%L,\n", output)
    }
    encodeBuilder.code.add("⇤)\n")

    if (returnType != null) {
      val returnValueCodeBlock = encodeBuilder.lift(
        values = listOf(CodeBlock.of("%N", "result")),
        encoder = encoderFactory.get(typeName = returnType),
      ) {
        encodeBuilder.coreTypeToValue()
      }
      encodeBuilder.code.add("return %L", returnValueCodeBlock)
    }

    return FunSpec.builder(value.kotlinName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        for (parameter in value.parameters) {
          addParameter(parameter.kotlinName, parameter.type.kotlinApi)
        }
      }
      .returns(value.returnType?.kotlinApi ?: UNIT)
      .addCode(encodeBuilder.build())
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
          for (coreType in encoder.coreTypes) {
            addParameter(receiver.name, coreType.kotlinCoreType)
          }
        }
        for (parameter in value.parameters) {
          val encoder = encoderFactory.get(parameter.type)
          for (coreType in encoder.coreTypes) {
            addParameter(parameter.kotlinName, coreType.kotlinCoreType)
          }
        }

        // TODO: flatten strings instead of calling single().
        val returnType = value.returnType
        if (returnType != null) {
          val encoder = encoderFactory.get(returnType)
          returns(encoder.coreTypes.single().kotlinCoreType)
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
        val encodeBuilder = GuestEncodeBuilder(
          bridge = CodeBlock.of("%T", Symbols.Brevity.GuestBridge),
        )

        val receiverCodeBlock = when (receiver) {
          is Receiver.Id -> {
            val encoder = encoderFactory.get(receiver.type)
            for (coreType in encoder.coreTypes) {
              addParameter(receiver.name, coreType.kotlinCoreType)
            }
            encodeBuilder.lift(
              values = listOf(CodeBlock.of("%N", receiver.name)),
              encoder = encoder,
            ) {
              encodeBuilder.coreTypeToValue()
            }
          }

          is Receiver.Global -> receiver.codeBlock
        }

        val parameterValues = mutableListOf<CodeBlock>()
        for (parameter in value.parameters) {
          val encoder = encoderFactory.get(typeName = parameter.type)
          for (coreType in encoder.coreTypes) {
            addParameter(parameter.kotlinName, coreType.kotlinCoreType)
          }
          parameterValues += encodeBuilder.lift(
            values = encoder.coreTypes.map { CodeBlock.of("%N", parameter.kotlinName) },
            encoder = encoder,
          ) {
            encodeBuilder.coreTypeToValue()
          }
        }

        val returnType = value.returnType
        if (returnType != null) {
          encodeBuilder.code.add("val %N = ", "result")
        }
        encodeBuilder.code.add("%L.%N(⇥\n", receiverCodeBlock, value.kotlinName)
        for ((index, parameter) in value.parameters.withIndex()) {
          encodeBuilder.code.add("%N = %L,\n", parameter.kotlinName, parameterValues[index])
        }
        encodeBuilder.code.add("⇤)\n")

        if (returnType != null) {
          val encoder = encoderFactory.get(typeName = returnType)
          val returnValue = encodeBuilder.lower(
            value = CodeBlock.of("%N", "result"),
            encoder = encoder,
          ) {
            encodeBuilder.valueToCoreType()
          }
          // TODO: flatten strings instead of calling single().
          returns(encoder.coreTypes.single().kotlinCoreType)
          encodeBuilder.code.add("return %L\n", returnValue.single())
        }

        addCode(encodeBuilder.build())
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

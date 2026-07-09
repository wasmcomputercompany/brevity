package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.ir.IrFunction
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory
import dev.wasmo.brevity.kotlin.encoders.GuestEncodeBuilder
import dev.wasmo.brevity.kotlin.encoders.coreParameters

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
      )
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
      val encoder = encoderFactory.get(typeName = returnType)
      val values = when (encoder.coreTypes.size) {
        1 -> listOf(CodeBlock.of("%N", "result"))
        else -> encodeBuilder.unflattenResult(CodeBlock.of("%N", "result"), encoder)
      }
      val returnValueCodeBlock = encodeBuilder.lift(
        values = values,
        encoder = encoder,
      )
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
        val nameAllocator = NameAllocator()
        if (receiver is Receiver.Id) {
          addParameters(encoderFactory.coreParameters(nameAllocator, receiver).specs)
        }
        for (parameter in value.parameters) {
          addParameters(encoderFactory.coreParameters(nameAllocator, parameter).specs)
        }

        // TODO: flatten strings instead of calling single().
        val returnType = value.returnType
        if (returnType != null) {
          val encoder = encoderFactory.get(returnType)
          when (encoder.coreTypes.size) {
            1 -> returns(encoder.coreTypes.single().kotlinCoreType)
            else -> returns(CoreType.Pointer.kotlinCoreType)
          }
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
            val coreParameters = encoderFactory.coreParameters(
              encodeBuilder.nameAllocator,
              receiver,
            )
            addParameters(coreParameters.specs)
            encodeBuilder.lift(coreParameters.values, coreParameters.encoder)
          }

          is Receiver.Global -> receiver.codeBlock
        }

        val parameterValues = mutableListOf<CodeBlock>()
        for (parameter in value.parameters) {
          val coreParameters = encoderFactory.coreParameters(
            encodeBuilder.nameAllocator,
            parameter,
          )
          addParameters(coreParameters.specs)
          parameterValues += encodeBuilder.lift(
            values = coreParameters.values,
            encoder = coreParameters.encoder,
          )
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
          val returnValues = encodeBuilder.lower(
            value = CodeBlock.of("%N", "result"),
            encoder = encoder,
          )
          when (encoder.coreTypes.size) {
            1 -> {
              returns(encoder.coreTypes.single().kotlinCoreType)
              encodeBuilder.code.add("return %L\n", returnValues.single())
            }

            else -> {
              returns(CoreType.Pointer.kotlinCoreType)
              encodeBuilder.code.add("return %L\n", encodeBuilder.flattenResult(returnValues, encoder))
            }
          }
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
      val name: Identifier
        get() = Identifier("self")
    }
  }
}

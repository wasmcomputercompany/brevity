package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.buildCodeBlock
import dev.wasmo.brevity.DeclarationIndex
import dev.wasmo.brevity.ir.IrResource
import dev.wasmo.brevity.ir.IrTypeName
import dev.wasmo.brevity.kotlin.generator.HostGenerator.Receiver

internal class HostBridgeBuilder(
  private val index: DeclarationIndex,
) {
  /** Returns a function that calls the guest. It implements the friendly API. */
  fun callGuestFunction(value: KtFunction): FunSpec {
    return FunSpec.builder(value.ktName)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        if (value.returnType != null) {
          addCode("val %N = ", "result")
        }
        addCode("%N.apply(⇥\n", value.ktName)

        for (parameter in value.parameters) {
          addParameter(parameter.name, parameter.irType.kotlinApi)
          addCode(
            hostApiToAbi(
              bridge = CodeBlock.of("%N", "bridge"),
              type = parameter.irType,
              apiValue = CodeBlock.of("%N", parameter.name),
            ),
          )
          addCode(",\n")
        }
        addCode("⇤)\n")

        if (value.returnType != null) {
          addCode(
            "return %L",
            hostAbiToApi(
              bridge = CodeBlock.of("%N", "bridge"),
              type = value.returnType,
              abiValue = CodeBlock.of("result[%L]", 0),
            ),
          )
          returns(value.returnType.kotlinApi)
        }
      }
      .build()
  }

  /** Adds a host function using the Chicory API. */
  fun declareHostFunction(
    bridge: CodeBlock,
    store: CodeBlock,
    receiver: Receiver,
    value: KtFunction,
  ): CodeBlock {
    if (!value.isSupported) return CodeBlock.of("/* TODO: ${value.ktName} */\n")

    val block = CodeBlock.builder()

    val abiParameterTypes = buildCodeBlock {
      when (receiver) {
        is Receiver.Id -> add("%T.I32", Symbols.ChicoryRuntime.ValType)
        else -> {}
      }
      for (parameter in value.parameters) {
        if (isNotEmpty()) add(", ")
        add(parameter.irType.toValType())
      }
    }

    var argIndex = 0
    when (receiver) {
      is Receiver.Id -> {
        block.addStatement(
          "val %N = %L",
          "self",
          receiver.codeBlock(CodeBlock.of("%N[%L]", "args", argIndex++)),
        )
      }

      is Receiver.Instance -> {
        block.addStatement(
          "val %N = %L",
          "self",
          receiver.codeBlock,
        )
      }
    }

    if (value.returnType != null) {
      block.add("val %N = ", "result")
    }
    block.add("%N.%N(⇥\n", "self", value.ktName)
    for (parameter in value.parameters) {
      block.add(
        "%L,\n",
        hostAbiToApi(
          bridge = bridge,
          type = parameter.irType,
          abiValue = CodeBlock.of("%N[%L]", "args", argIndex++),
        ),
      )
    }
    block.add("⇤)\n")
    if (value.returnType != null) {
      block.add(
        "return@%T longArrayOf(%L)",
        Symbols.ChicoryRuntime.WasmFunctionHandle,
        hostApiToAbi(
          bridge = bridge,
          type = value.returnType,
          apiValue = CodeBlock.of("%N", "result"),
        ),
      )
    } else {
      block.add(
        "return@%T longArrayOf()",
        Symbols.ChicoryRuntime.WasmFunctionHandle,
      )
    }

    return CodeBlock.of(
      """
      |%L.addFunction(
      |  %T(
      |    %L,
      |    %S,
      |    %T.of(
      |      listOf(%L),
      |      listOf(%L),
      |    ),
      |    %T { instance, args ->
      |      ⇥⇥⇥%L⇤⇤⇤
      |    },
      |  )
      |)
      |
      """.trimMargin(),
      store,
      Symbols.ChicoryRuntime.HostFunction,
      value.name.moduleName?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null"),
      value.name.abiName,
      Symbols.ChicoryRuntime.FunctionType,
      abiParameterTypes,
      value.returnType?.toValType() ?: CodeBlock.of(""),
      Symbols.ChicoryRuntime.WasmFunctionHandle,
      block.build(),
    )
  }

  /** Lift an ABI value like a memory address to an API value like a resource instance. */
  fun hostAbiToApi(
    bridge: CodeBlock,
    type: IrTypeName,
    abiValue: CodeBlock,
  ): CodeBlock {
    return when (type) {
      else -> CodeBlock.of(
        "%L as %T",
        abiValue,
        type.kotlinApi,
      )
    }
  }

  /** Lower an API value like a resource instance to an ABI value like a memory address. */
  fun hostApiToAbi(
    bridge: CodeBlock,
    type: IrTypeName,
    apiValue: CodeBlock,
  ): CodeBlock {
    return when (type) {
      is IrTypeName.Declared -> {
        when (index[type]) {
          is IrResource -> CodeBlock.of(
            "%L.toId(%L)",
            bridge,
            apiValue,
          )

          else -> CodeBlock.of(
            "%L as %T",
            apiValue,
            LONG,
          )
        }
      }

      else -> CodeBlock.of(
        "%L as %T",
        apiValue,
        LONG,
      )
    }
  }

  private fun IrTypeName.toValType() =
    CodeBlock.of("%T.I32", Symbols.ChicoryRuntime.ValType)
}

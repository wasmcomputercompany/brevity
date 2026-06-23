package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

/** Lift an ABI value like a memory address to an API value like a resource instance. */
internal fun abiToApi(
  bridge: CodeBlock,
  type: KtTypeName,
  abiValue: CodeBlock,
): CodeBlock {
  return when (type) {
    is KtTypeName.Declared -> {
      when (type.codec) {
        KtTypeName.Declared.Codec.Resource -> CodeBlock.of(
          "%L.fromId(%L, ::%T)",
          bridge,
          abiValue,
          bridgedType(type.apiType),
        )

        else -> abiValue
      }
    }

    else -> abiValue
  }
}

/** Lower an API value like a resource instance to an ABI value like a memory address. */
internal fun apiToAbi(
  bridge: CodeBlock,
  type: KtTypeName,
  apiValue: CodeBlock,
): CodeBlock {
  return when (type) {
    is KtTypeName.Declared -> {
      when (type.codec) {
        KtTypeName.Declared.Codec.Resource -> CodeBlock.of(
          "%L.toId(%L)",
          bridge,
          apiValue,
        )

        else -> apiValue
      }
    }

    else -> apiValue
  }
}

internal fun bridgedType(resourceType: ClassName): ClassName =
  ClassName(resourceType.packageName, "Bridged${resourceType.simpleName}")

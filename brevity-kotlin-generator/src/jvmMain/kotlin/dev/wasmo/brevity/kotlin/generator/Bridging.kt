package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.LONG

/** Lift an ABI value like a memory address to an API value like a resource instance. */
internal fun guestAbiToApi(
  bridge: CodeBlock,
  type: KtTypeName,
  abiValue: CodeBlock,
): CodeBlock {
  return when (type) {
    is KtTypeName.Declared -> {
      when (type.codec) {
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
internal fun guestApiToAbi(
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

/** Lift an ABI value like a memory address to an API value like a resource instance. */
internal fun hostAbiToApi(
  bridge: CodeBlock,
  type: KtTypeName,
  abiValue: CodeBlock,
): CodeBlock {
  return when (type) {
    else -> CodeBlock.of(
      "%L as %T",
      abiValue,
      type.apiType,
    )
  }
}

/** Lower an API value like a resource instance to an ABI value like a memory address. */
internal fun hostApiToAbi(
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

internal fun bridgedType(resourceType: ClassName): ClassName =
  ClassName(resourceType.packageName, "Bridged${resourceType.simpleName}")

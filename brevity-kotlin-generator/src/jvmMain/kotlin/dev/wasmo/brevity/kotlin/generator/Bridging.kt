package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.LONG
import dev.wasmo.brevity.Annotation

/** Lift an ABI value like a memory address to an API value like a resource instance. */
internal fun guestAbiToApi(
  index: DeclarationIndex,
  bridge: CodeBlock,
  type: KtTypeName,
  abiValue: CodeBlock,
): CodeBlock {
  return when (type) {
    is KtTypeName.Declared -> {
      when (index[type.apiType]) {
        is KtResource -> CodeBlock.of(
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
  index: DeclarationIndex,
  bridge: CodeBlock,
  type: KtTypeName,
  apiValue: CodeBlock,
): CodeBlock {
  return when (type) {
    is KtTypeName.Declared -> {
      when (index[type.apiType]) {
        is KtResource -> CodeBlock.of(
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

val KtResource.handleName: ClassName
  get() = ClassName(type.packageName, "${type.simpleName}Handle")

val KtTypeName.Declared.handleName: ClassName
  get() = ClassName(apiType.packageName, "${apiType.simpleName}Handle")

/** Returns true if we've done the work to implement this. */
val KtFunction.isSupported: Boolean
  get() = name.annotation == null ||
    name.annotation == Annotation.Method ||
    name.annotation == Annotation.ResourceDrop

package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import dev.wasmo.brevity.kotlin.abi.AbiFunction
import dev.wasmo.brevity.kotlin.abi.AbiLower
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.Platform
import dev.wasmo.brevity.kotlin.encoders.CoreType
import dev.wasmo.brevity.kotlin.expressions.CodeBlockExpression
import dev.wasmo.brevity.kotlin.expressions.valueExpression

/**
 * The Kotlin implementation of a WIT function. These functions implement the interface generated
 * by [ApiFunctionFactory].
 */
class PlatformFunctionFactory(
  private val apiFunctionFactory: ApiFunctionFactory,
  private val platform: Platform,
) {
  fun create(
    bridge: CodeBlock,
    abiFunction: AbiFunction,
  ): FunSpec {
    return apiFunctionFactory.builder(abiFunction)
      .addModifiers(KModifier.OVERRIDE)
      .apply {
        val codeBuilder = CodeBuilder(
          bridge = bridge,
          platform = platform,
          nameAllocator = abiFunction.newNameAllocator(),
        )

        context(codeBuilder) {
          val abiLower = AbiLower(
            abiFunction = abiFunction,
            resourceId = when {
              abiFunction.isResource -> CodeBlockExpression(
                type = CoreType.I32.kotlinCoreType,
                nameHint = "id",
                code = CodeBlock.of("this.%N", "id"),
                immediate = true,
              )

              else -> null
            },
          )
          val returnValue = abiLower.call(
            receiver = null,
            parameters = abiFunction.parameters.liftedSpecs.map { it.valueExpression },
          )

          codeBuilder.platform.afterLiftResult()

          val resultValue = abiFunction.result.value
          if (resultValue != null) {
            codeBuilder.add("return %L", returnValue.code)
          }
        }
        addCode(codeBuilder.build())
      }
      .build()
  }
}

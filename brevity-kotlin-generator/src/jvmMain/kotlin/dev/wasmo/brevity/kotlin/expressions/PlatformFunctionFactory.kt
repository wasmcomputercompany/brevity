package dev.wasmo.brevity.kotlin.expressions

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import dev.wasmo.brevity.kotlin.code.CodeBuilder
import dev.wasmo.brevity.kotlin.code.Platform

/**
 * The Kotlin implementation of a WIT function.
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
          val abiLower = AbiLower(abiFunction, platform.createLowered(abiFunction))
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

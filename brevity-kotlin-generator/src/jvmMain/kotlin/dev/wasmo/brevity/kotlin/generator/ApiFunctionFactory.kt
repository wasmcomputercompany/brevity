package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT

internal class ApiFunctionFactory(
  private val function: BridgeFunction,
) {
  fun api() = FunSpec.builder(function.kotlinName)
    .addModifiers(KModifier.ABSTRACT)
    .addParameters(function.liftedParameters.map { it.spec })
    .returns(function.loweredResult.result?.kotlinType ?: UNIT)
    .apply {
      if (function.async) {
        addModifiers(KModifier.SUSPEND)
      }
      if (function.documentation != null) {
        addKdoc("%L", function.documentation)
      }
    }
    .build()
}

package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.kotlin.KotlinMapper

internal class ApiFunctionFactory(
  private val kotlinMapper: KotlinMapper,
  private val function: BridgeFunction,
) {
  fun api() = FunSpec.builder(function.kotlinName)
    .addModifiers(KModifier.ABSTRACT)
    .addParameters(function.liftedParameterSpecs)
    .returns(function.function.returnType?.let { kotlinMapper.get(it) } ?: UNIT)
    .apply {
      if (function.function.async && function.supportAsync) {
        addModifiers(KModifier.SUSPEND)
      }
      val kdoc = buildString {
        val functionDocumentation = function.function.documentation
        if (functionDocumentation != null) {
          append(functionDocumentation.content.trimIndent())
          append("\n\n")
        }

        for (parameter in function.function.parameters) {
          val parameterDocumentation = parameter.documentation ?: continue
          val parameterName = function.nameAllocator[parameter.name]
          append("@param $parameterName ")
          append(parameterDocumentation.content.trimIndent().replace("\n", "\n  "))
          append("\n\n")
        }
      }.trim()
      if (kdoc.isNotEmpty()) {
        addKdoc("%L", kdoc)
      }
    }
    .build()
}

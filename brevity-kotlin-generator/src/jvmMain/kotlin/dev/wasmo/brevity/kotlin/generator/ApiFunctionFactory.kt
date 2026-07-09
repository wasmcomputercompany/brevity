package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.UNIT
import dev.wasmo.brevity.ir.IrFunction

internal class ApiFunctionFactory(
  private val value: IrFunction,
) {
  private val nameAllocator = NameAllocator().apply {
    // Pre-allocate all the names we'll need.
    for (parameter in value.parameters) {
      newName(parameter.kotlinName, parameter.name)
    }
  }

  fun api() = FunSpec.builder(value.kotlinName)
    .addModifiers(KModifier.ABSTRACT)
    .apply {
      val documentation = value.documentation
      if (documentation != null) {
        addKdoc(documentation.content.trimIndent())
      }

      for (parameter in value.parameters) {
        addParameter(nameAllocator[parameter.name], parameter.type.kotlinApi)
      }

      returns(value.returnType?.kotlinApi ?: UNIT)
    }
    .build()
}

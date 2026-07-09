package dev.wasmo.brevity.kotlin.generator

import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import dev.wasmo.brevity.Identifier
import dev.wasmo.brevity.TypeName
import dev.wasmo.brevity.kotlin.encoders.Encoder
import dev.wasmo.brevity.kotlin.encoders.EncoderFactory

/**
 * A list of parameters that use core types only, to be lifted on inbound calls and lowered on
 * outbound calls.
 */
class CoreParameter(
  val encoder: Encoder,
  val specs: List<ParameterSpec>,
  val names: List<String>,
) {
  class Factory(
    val encoderFactory: EncoderFactory,
    val nameAllocator: NameAllocator,
  ) {
    operator fun invoke(name: Identifier, typeName: TypeName): CoreParameter {
      val encoder = encoderFactory.get(typeName)
      val nameHints = encoder.nameHints

      val specs = mutableListOf<ParameterSpec>()
      val names = mutableListOf<String>()
      for ((index, coreType) in encoder.coreTypes.withIndex()) {
        val nameHint = nameHints?.getOrNull(index)
        val coreName = when {
          nameHint != null -> nameAllocator.newName(
            Identifier("${name}-${nameHint.name}").toCamelCase(upperCamel = false),
          )

          else -> nameAllocator[name]
        }
        specs += ParameterSpec(coreName, coreType.kotlinCoreType)
        names += coreName
      }

      return CoreParameter(
        encoder = encoder,
        specs = specs,
        names = names,
      )
    }
  }
}
